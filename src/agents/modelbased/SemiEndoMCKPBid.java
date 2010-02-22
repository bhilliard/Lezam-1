package agents.modelbased;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import models.AbstractModel;
import models.bidtocpc.AbstractBidToCPC;
import models.bidtocpc.WEKAEnsembleBidToCPC;
import models.bidtoprclick.AbstractBidToPrClick;
import models.bidtoprclick.WEKAEnsembleBidToPrClick;
import models.prconv.AbstractConversionModel;
import models.prconv.BasicConvPrModel;
import models.prconv.GoodConversionPrModel;
import models.prconv.HistoricPrConversionModel;
import models.querytonumimp.AbstractQueryToNumImp;
import models.querytonumimp.BasicQueryToNumImp;
import models.sales.SalesDistributionModel;
import models.targeting.BasicTargetModel;
import models.unitssold.AbstractUnitsSoldModel;
import models.unitssold.BasicUnitsSoldModel;
import models.usermodel.AbstractUserModel;
import models.usermodel.BasicUserModel;
import agents.AbstractAgent;
import agents.AbstractAgent.Predictions;
import agents.modelbased.mckputil.IncItem;
import agents.modelbased.mckputil.Item;
import agents.modelbased.mckputil.ItemComparatorByWeight;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Product;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.QueryType;
import edu.umich.eecs.tac.props.SalesReport;

/**
 * @author jberg, spucci, vnarodit
 *
 */
public class SemiEndoMCKPBid extends AbstractAgent {

	private int MAX_TIME_HORIZON = 5;
	private boolean SAFETYBUDGET = false;
	private boolean BUDGET = false;
	private boolean FORWARDUPDATING = false;
	private boolean PRICELINES = false;

	private double _safetyBudget = 800;

	private Random _R = new Random();
	private boolean DEBUG = false;
	private double LAMBDA = .995;
	private HashMap<Query, Double> _salesPrices;
	private HashMap<Query, Double> _baseConvProbs;
	private HashMap<Query, Double> _baseClickProbs;
	private AbstractUserModel _userModel;
	private AbstractQueryToNumImp _queryToNumImpModel;
	private AbstractBidToCPC _bidToCPC;
	private AbstractBidToPrClick _bidToPrClick;
	private AbstractUnitsSoldModel _unitsSold;
	private AbstractConversionModel _convPrModel;
	private SalesDistributionModel _salesDist;
	private BasicTargetModel _targModel;
	private ArrayList<Double> bidList;
	private int lagDays = 5;
	private boolean salesDistFlag;

	public SemiEndoMCKPBid() {
		this(false,false,false);
	}


	public SemiEndoMCKPBid(boolean forward, boolean pricelines, boolean budget) {
		BUDGET = budget;
		FORWARDUPDATING = forward;
		PRICELINES = pricelines;
		_R.setSeed(124962748);
		bidList = new ArrayList<Double>();
		//		double increment = .25;
		double increment  = .05;
		double min = .04;
		double max = 1.65;
		int tot = (int) Math.ceil((max-min) / increment);
		for(int i = 0; i < tot; i++) {
			bidList.add(min+(i*increment));
		}

		salesDistFlag = false;
	}



	@Override
	public Set<AbstractModel> initModels() {
		/*
		 * Order is important because some of our models use other models
		 * so we use a LinkedHashSet
		 */
		Set<AbstractModel> models = new LinkedHashSet<AbstractModel>();
		AbstractUserModel userModel = new BasicUserModel();
		AbstractQueryToNumImp queryToNumImp = new BasicQueryToNumImp(userModel);
		AbstractUnitsSoldModel unitsSold = new BasicUnitsSoldModel(_querySpace,_capacity,_capWindow);
		BasicTargetModel basicTargModel = new BasicTargetModel(_manSpecialty,_compSpecialty);
		AbstractBidToCPC bidToCPC = new WEKAEnsembleBidToCPC(_querySpace, 10, 10, true, false);
		AbstractBidToPrClick bidToPrClick = new WEKAEnsembleBidToPrClick(_querySpace, 10, 10, basicTargModel, true, true);
		BasicConvPrModel convPrModel = new BasicConvPrModel(userModel, _querySpace, _baseConvProbs);
		models.add(userModel);
		models.add(queryToNumImp);
		models.add(bidToCPC);
		models.add(bidToPrClick);
		models.add(unitsSold);
		models.add(convPrModel);
		models.add(basicTargModel);
		return models;
	}

	protected void buildMaps(Set<AbstractModel> models) {
		for(AbstractModel model : models) {
			if(model instanceof AbstractUserModel) {
				AbstractUserModel userModel = (AbstractUserModel) model;
				_userModel = userModel;
			}
			else if(model instanceof AbstractQueryToNumImp) {
				AbstractQueryToNumImp queryToNumImp = (AbstractQueryToNumImp) model;
				_queryToNumImpModel = queryToNumImp;
			}
			else if(model instanceof AbstractUnitsSoldModel) {
				AbstractUnitsSoldModel unitsSold = (AbstractUnitsSoldModel) model;
				_unitsSold = unitsSold;
			}
			else if(model instanceof AbstractBidToCPC) {
				AbstractBidToCPC bidToCPC = (AbstractBidToCPC) model;
				_bidToCPC = bidToCPC; 
			}
			else if(model instanceof AbstractBidToPrClick) {
				AbstractBidToPrClick bidToPrClick = (AbstractBidToPrClick) model;
				_bidToPrClick = bidToPrClick;
			}
			else if(model instanceof AbstractConversionModel) {
				AbstractConversionModel convPrModel = (AbstractConversionModel) model;
				_convPrModel = convPrModel;
			}
			else if(model instanceof BasicTargetModel) {
				BasicTargetModel targModel = (BasicTargetModel) model;
				_targModel = targModel;
			}
			else {
				//				throw new RuntimeException("Unhandled Model (you probably would have gotten a null pointer later)"+model);
			}
		}
	}

	@Override
	public void initBidder() {

		_baseConvProbs = new HashMap<Query, Double>();
		_baseClickProbs = new HashMap<Query, Double>();

		// set revenue prices
		_salesPrices = new HashMap<Query,Double>();
		for(Query q : _querySpace) {

			String manufacturer = q.getManufacturer();
			if(_manSpecialty.equals(manufacturer)) {
				_salesPrices.put(q, 10*(_MSB+1));
			}
			else if(manufacturer == null) {
				_salesPrices.put(q, (10*(_MSB+1)) * (1/3.0) + (10)*(2/3.0));
			}
			else {
				_salesPrices.put(q, 10.0);
			}

			if(q.getType() == QueryType.FOCUS_LEVEL_ZERO) {
				_baseConvProbs.put(q, _piF0);
			}
			else if(q.getType() == QueryType.FOCUS_LEVEL_ONE) {
				_baseConvProbs.put(q, _piF1);
			}
			else if(q.getType() == QueryType.FOCUS_LEVEL_TWO) {
				_baseConvProbs.put(q, _piF2);
			}
			else {
				throw new RuntimeException("Malformed query");
			}

			/*
			 * These are the MAX e_q^a (they are randomly generated), which is our clickPr for being in slot 1!
			 * 
			 * Taken from the spec
			 */

			if(q.getType() == QueryType.FOCUS_LEVEL_ZERO) {
				_baseClickProbs.put(q, .3);
			}
			else if(q.getType() == QueryType.FOCUS_LEVEL_ONE) {
				_baseClickProbs.put(q, .4);
			}
			else if(q.getType() == QueryType.FOCUS_LEVEL_TWO) {
				_baseClickProbs.put(q, .5);
			}
			else {
				throw new RuntimeException("Malformed query");
			}
		}
	}


	@Override
	public void updateModels(SalesReport salesReport, QueryReport queryReport) {

		for(AbstractModel model: _models) {
			if(model instanceof AbstractUserModel) {
				AbstractUserModel userModel = (AbstractUserModel) model;
				userModel.updateModel(queryReport, salesReport);
			}
			else if(model instanceof AbstractQueryToNumImp) {
				AbstractQueryToNumImp queryToNumImp = (AbstractQueryToNumImp) model;
				queryToNumImp.updateModel(queryReport, salesReport);
			}
			else if(model instanceof AbstractUnitsSoldModel) {
				AbstractUnitsSoldModel unitsSold = (AbstractUnitsSoldModel) model;
				unitsSold.update(salesReport);
			}
			else if(model instanceof AbstractBidToCPC) {
				AbstractBidToCPC bidToCPC = (AbstractBidToCPC) model;
				bidToCPC.updateModel(queryReport, salesReport, _bidBundles.get(_bidBundles.size()-2));
			}
			else if(model instanceof AbstractBidToPrClick) {
				AbstractBidToPrClick bidToPrClick = (AbstractBidToPrClick) model;
				bidToPrClick.updateModel(queryReport, salesReport, _bidBundles.get(_bidBundles.size()-2));
			}
			else if(model instanceof AbstractConversionModel) {
				AbstractConversionModel convPrModel = (AbstractConversionModel) model;
				int timeHorizon = (int) Math.min(Math.max(1,_day - 1), MAX_TIME_HORIZON);
				if(model instanceof GoodConversionPrModel) {
					GoodConversionPrModel adMaxModel = (GoodConversionPrModel) convPrModel;
					adMaxModel.setTimeHorizon(timeHorizon);
				}
				if(model instanceof HistoricPrConversionModel) {
					HistoricPrConversionModel adMaxModel = (HistoricPrConversionModel) convPrModel;
					adMaxModel.setTimeHorizon(timeHorizon);
				}
				convPrModel.updateModel(queryReport, salesReport,_bidBundles.get(_bidBundles.size()-2));
			}
			else if(model instanceof BasicTargetModel) {
				//Do nothing
			}
			else {
				//				throw new RuntimeException("Unhandled Model (you probably would have gotten a null pointer later)");
			}
		}
	}


	@Override
	public BidBundle getBidBundle(Set<AbstractModel> models) {
		double start = System.currentTimeMillis();
		BidBundle bidBundle = new BidBundle();

		if(SAFETYBUDGET) {
			bidBundle.setCampaignDailySpendLimit(_safetyBudget);
		}

		if(_day > 1) {
			if(!salesDistFlag) {
				SalesDistributionModel salesDist = new SalesDistributionModel(_querySpace);
				_salesDist = salesDist;
				salesDistFlag = true;
			}
			_salesDist.updateModel(_salesReport);
		}

		if(_day > lagDays){
			buildMaps(models);
			double remainingCap;
			if(_day < 4) {
				remainingCap = _capacity/_capWindow;
			}
			else {
				//				budget = Math.max(20,_capacity*(2.0/5.0) - _unitsSold.getWindowSold()/4);
				remainingCap = _capacity - _unitsSold.getWindowSold();
				debug("Unit Sold Model Budget "  +remainingCap);
			}

			debug("Budget: "+ remainingCap);
			//NEED TO USE THE MODELS WE ARE PASSED!!!

			ArrayList<IncItem> allIncItems = new ArrayList<IncItem>();

			//want the queries to be in a guaranteed order - put them in an array
			//index will be used as the id of the query
			double penalty = getPenalty(remainingCap, 0);
			HashMap<Query,ArrayList<Predictions>> allPredictionsMap = new HashMap<Query, ArrayList<Predictions>>();
			for(Query q : _querySpace) {
				ArrayList<Item> itemList = new ArrayList<Item>();
				ArrayList<Predictions> queryPredictions = new ArrayList<Predictions>();
				debug("Query: " + q);
				for(int i = 0; i < bidList.size(); i++) {
					double salesPrice = _salesPrices.get(q);
					double bid = bidList.get(i);
					double clickPr = _bidToPrClick.getPrediction(q, bid, new Ad());
					double numImps = _queryToNumImpModel.getPrediction(q,(int) (_day+1));
					int numClicks = (int) (clickPr * numImps);
					double CPC = _bidToCPC.getPrediction(q, bid);
					double convProb = _convPrModel.getPrediction(q);

					if(Double.isNaN(CPC)) {
						CPC = 0.0;
					}

					if(Double.isNaN(clickPr)) {
						clickPr = 0.0;
					}

					if(Double.isNaN(convProb)) {
						convProb = 0.0;
					}

					debug("\tBid: " + bid);
					debug("\tCPC: " + CPC);
					debug("\tNumImps: " + numImps);
					debug("\tNumClicks: " + numClicks);
					debug("\tClickPr: " + clickPr);
					debug("\tConv Prob: " + convProb + "\n\n");

					double convProbWithPen = getConversionPrWithPenalty(q, penalty);

					double w = numClicks*convProbWithPen;				//weight = numClciks * convProv
					double v = numClicks*convProbWithPen*salesPrice - numClicks*CPC;	//value = revenue - cost	[profit]
					itemList.add(new Item(q,w,v,bid,false,0,i));
					queryPredictions.add(new Predictions(clickPr, CPC, convProb, numImps));
				}
				debug("Items for " + q);
				Item[] items = itemList.toArray(new Item[0]);
				IncItem[] iItems = getIncremental(items);
				allIncItems.addAll(Arrays.asList(iItems));
				allPredictionsMap.put(q, queryPredictions);
			}

			Collections.sort(allIncItems);
			HashMap<Query,Item> solution = fillKnapsackWithCapExt(allIncItems, remainingCap, allPredictionsMap);

			//set bids
			for(Query q : _querySpace) {
				ArrayList<Predictions> queryPrediction = allPredictionsMap.get(q);
				double bid;

				if(solution.containsKey(q)) {
					int bidIdx = solution.get(q).idx();
					Predictions predictions = queryPrediction.get(bidIdx);
					double clickPr = predictions.getClickPr();
					double numImps = predictions.getNumImp();
					int numClicks = (int) (clickPr * numImps);
					double CPC = predictions.getCPC();

					if(solution.get(q).targ()) {

						bidBundle.setBid(q, bidList.get(bidIdx));

						if (q.getType().equals(QueryType.FOCUS_LEVEL_ZERO))
							bidBundle.setAd(q, new Ad(new Product(_manSpecialty, _compSpecialty)));
						if (q.getType().equals(QueryType.FOCUS_LEVEL_ONE) && q.getComponent() == null)
							bidBundle.setAd(q, new Ad(new Product(q.getManufacturer(), _compSpecialty)));
						if (q.getType().equals(QueryType.FOCUS_LEVEL_ONE) && q.getManufacturer() == null)
							bidBundle.setAd(q, new Ad(new Product(_manSpecialty, q.getComponent())));
						if (q.getType().equals(QueryType.FOCUS_LEVEL_TWO) && q.getManufacturer().equals(_manSpecialty)) 
							bidBundle.setAd(q, new Ad(new Product(_manSpecialty, q.getComponent())));
					}
					else {
						bidBundle.addQuery(q, bidList.get(bidIdx), new Ad());
					}

					if(BUDGET) {
						bidBundle.setDailyLimit(q, numClicks*CPC);
					}
				}
				else {
					/*
					 * We decided that we did not want to be in this query, so we will use it to explore the space
					 */
					//					bid = 0.0;
					//					bidBundle.addQuery(q, bid, new Ad(), Double.NaN);
					//					System.out.println("Bidding " + bid + "   for query: " + q);

					if (q.getType().equals(QueryType.FOCUS_LEVEL_ZERO))
						bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);
					else if (q.getType().equals(QueryType.FOCUS_LEVEL_ONE))
						bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);
					else
						bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);

					//					System.out.println("Exploring " + q + "   bid: " + bid);
					bidBundle.addQuery(q, bid, new Ad(), bid*10);
				}
			}

			/*
			 * Pass expected conversions to unit sales model
			 */
			double solutionWeight = solutionWeight(remainingCap,solution,allPredictionsMap);
			((BasicUnitsSoldModel)_unitsSold).expectedConvsTomorrow((int) solutionWeight);
		}
		else {
			for(Query q : _querySpace){
				double bid = 0.0;
				if (q.getType().equals(QueryType.FOCUS_LEVEL_ZERO))
					bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);
				else if (q.getType().equals(QueryType.FOCUS_LEVEL_ONE))
					bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);
				else
					bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);
				bidBundle.addQuery(q, bid, new Ad(), Double.NaN);
			}
		}
		double stop = System.currentTimeMillis();
		double elapsed = stop - start;
		//		System.out.println("This took " + (elapsed / 1000) + " seconds");
		return bidBundle;
	}

	private double getPenalty(double remainingCap, double solutionWeight) {
		double penalty;
		solutionWeight = Math.max(0,solutionWeight);
		if(remainingCap < 0) {
			if(solutionWeight <= 0) {
				penalty = Math.pow(LAMBDA, Math.abs(remainingCap));
			}
			else {
				penalty = 0.0;
				int num = 0;
				for(double j = Math.abs(remainingCap)+1; j <= Math.abs(remainingCap)+solutionWeight; j++) {
					penalty += Math.pow(LAMBDA, j);
					num++;
				}
				penalty /= (num);
			}
		}
		else {
			if(solutionWeight <= 0) {
				penalty = 1.0;
			}
			else {
				if(solutionWeight > remainingCap) {
					penalty = remainingCap;
					for(int j = 1; j <= solutionWeight-remainingCap; j++) {
						penalty += Math.pow(LAMBDA, j);
					}
					penalty /= (solutionWeight);
				}
				else {
					penalty = 1.0;
				}
			}
		}
		if(Double.isNaN(penalty)) {
			penalty = 1.0;
		}
		return penalty;
	}

	private double[] solutionValueMultiDay2(HashMap<Query, Item> solution, double remainingCap, HashMap<Query,ArrayList<Predictions>> allPredictionsMap, int numDays) {
		double totalWeight = solutionWeight(remainingCap, solution, allPredictionsMap);
		double penalty = getPenalty(remainingCap, totalWeight);

		double totalValue = 0;
		for(Query q : _querySpace) {
			if(solution.containsKey(q)) {
				Item item = solution.get(q);
				Predictions prediction = allPredictionsMap.get(item.q()).get(item.idx());
				totalValue += prediction.getClickPr()*prediction.getNumImp()*(getConversionPrWithPenalty(q, penalty)*_salesPrices.get(item.q()) - prediction.getCPC());
			}
		}

		double daysLookahead = Math.max(0, Math.min(numDays, 58 - _day));
		if(daysLookahead > 0 && totalWeight > 0) {
			ArrayList<Integer> soldArrayTMP = ((BasicUnitsSoldModel) _unitsSold).getSalesArray();
			ArrayList<Integer> soldArray = getCopy(soldArrayTMP);

			Integer expectedConvsYesterday = ((BasicUnitsSoldModel) _unitsSold).getExpectedConvsTomorrow();
			if(expectedConvsYesterday == null) {
				expectedConvsYesterday = 0;
				int counter2 = 0;
				for(int j = 0; j < 5 && j < soldArray.size(); j++) {
					expectedConvsYesterday += soldArray.get(soldArray.size()-1-j);
					counter2++;
				}
				expectedConvsYesterday /= (double)counter2;
			}
			soldArray.add(expectedConvsYesterday);
			soldArray.add((int) totalWeight);

			for(int i = 0; i < daysLookahead; i++) {
				double expectedBudget = _capacity;
				for(int j = 0; j < _capWindow-1; j++) {
					expectedBudget -= soldArray.get(soldArray.size()-1-j);
				}

				double numSales = solutionWeight(expectedBudget, solution, allPredictionsMap);
				soldArray.add((int) numSales);

				double penaltyNew = getPenalty(expectedBudget, numSales);
				for(Query q : _querySpace) {
					if(solution.containsKey(q)) {
						Item item = solution.get(q);
						Predictions prediction = allPredictionsMap.get(item.q()).get(item.idx());
						totalValue += prediction.getClickPr()*prediction.getNumImp()*(getConversionPrWithPenalty(q, penaltyNew)*_salesPrices.get(item.q()) - prediction.getCPC());
					}
				}
			}
		}
		double[] output = new double[2];
		output[0] = totalValue;
		output[1] = totalWeight;
		return output;
	}

	private double[] solutionValueMultiDay(HashMap<Query, Item> solution, double remainingCap, HashMap<Query,ArrayList<Predictions>> allPredictionsMap) {
		double totalWeight = solutionWeight(remainingCap, solution, allPredictionsMap);

		double penalty = getPenalty(remainingCap, totalWeight);

		double totalValue = 0;
		double solWeight = 0;
		HashMap<Query, Double> currentSalesDist = new HashMap<Query,Double>();
		for(Query q : _querySpace) {
			if(solution.containsKey(q)) {
				Item item = solution.get(q);
				Predictions prediction = allPredictionsMap.get(item.q()).get(item.idx());
				totalValue += prediction.getClickPr()*prediction.getNumImp()*(getConversionPrWithPenalty(q, penalty)*_salesPrices.get(item.q()) - prediction.getCPC());
				double tmpWeight = prediction.getClickPr()*prediction.getNumImp()*getConversionPrWithPenalty(q, penalty);
				solWeight += tmpWeight;
				currentSalesDist.put(q, tmpWeight);
			}
		}

		for(Query q : _querySpace) {
			if(currentSalesDist.containsKey(q)) {
				currentSalesDist.put(q, currentSalesDist.get(q)/solWeight);
			}
			else {
				currentSalesDist.put(q, 0.0);
			}
		}

		double valueLostWindow = Math.max(0, Math.min(_capWindow-1, 58 - _day));
		//		double valueLostWindow = Math.max(1, Math.min(_capWindow, 58 - _day));
		double multiDayLoss = 0;
		if(valueLostWindow > 0 && solWeight > 0) {
			ArrayList<Integer> soldArrayTMP = ((BasicUnitsSoldModel) _unitsSold).getSalesArray();
			ArrayList<Integer> soldArray = getCopy(soldArrayTMP);

			Integer expectedConvsYesterday = ((BasicUnitsSoldModel) _unitsSold).getExpectedConvsTomorrow();
			if(expectedConvsYesterday == null) {
				expectedConvsYesterday = 0;
				int counter2 = 0;
				for(int j = 0; j < 5 && j < soldArray.size(); j++) {
					expectedConvsYesterday += soldArray.get(soldArray.size()-1-j);
					counter2++;
				}
				expectedConvsYesterday /= (double)counter2;
			}
			soldArray.add(expectedConvsYesterday);
			soldArray.add((int) solWeight);

			for(int i = 0; i < valueLostWindow; i++) {
				double expectedBudget = _capacity;

				for(int j = 0; j < _capWindow-1; j++) {
					expectedBudget -= soldArray.get(soldArray.size()-1-j);
				}

				double valueLost = 0;

				double numSales = solutionWeight(expectedBudget, solution, allPredictionsMap);
				soldArray.add((int) numSales);

				for(int j = 1; j <= numSales; j++) {
					if(expectedBudget - j <= 0) {
						valueLost += 1.0/Math.pow(LAMBDA, Math.abs(expectedBudget-j-1)) - 1.0/Math.pow(LAMBDA, Math.abs(expectedBudget-j));
					}
				}

				double avgConvProb = 0; //the average probability of conversion;
				for(Query q : _querySpace) {
					avgConvProb += getConversionPrWithPenalty(q, getPenalty(expectedBudget, numSales)) * currentSalesDist.get(q);
				}

				double avgCPC = 0;
				for(Query q : _querySpace) {
					if(solution.containsKey(q)) {
						Item item = solution.get(q);
						Predictions prediction = allPredictionsMap.get(item.q()).get(item.idx());
						avgCPC += prediction.getCPC() * currentSalesDist.get(q);
					}
				}

				double avgCPA = avgCPC/avgConvProb;

				multiDayLoss += Math.max(valueLost*avgCPA,0);
			}
		}
		double[] output = new double[2];
		output[0] = totalValue-multiDayLoss;
		output[1] = solWeight;
		return output;
	}

	private ArrayList<Integer> getCopy(ArrayList<Integer> soldArrayTMP) {
		ArrayList<Integer> soldArray = new ArrayList<Integer>(soldArrayTMP.size());
		for(int i = 0; i < soldArrayTMP.size(); i++) {
			soldArray.add(soldArrayTMP.get(i));
		}
		return soldArray;
	}


	private double solutionWeight(double budget, HashMap<Query, Item> solution, HashMap<Query, ArrayList<Predictions>> allPredictionsMap, BidBundle bidBundle) {
		double threshold = .5;
		int maxIters = 40;
		double lastSolWeight = Double.MAX_VALUE;
		double solutionWeight = 0.0;

		/*
		 * As a first estimate use the weight of the solution
		 * with no penalty
		 */
		for(Query q : _querySpace) {
			if(solution.get(q) == null) {
				continue;
			}
			Predictions predictions = allPredictionsMap.get(q).get(solution.get(q).idx());
			double dailyLimit = Double.NaN;
			if(bidBundle != null) {
				dailyLimit  = bidBundle.getDailyLimit(q);
			}
			double clickPr = predictions.getClickPr();
			double numImps = predictions.getNumImp();
			int numClicks = (int) (clickPr * numImps);
			double CPC = predictions.getCPC();
			double convProb = getConversionPrWithPenalty(q, 1.0);

			if(Double.isNaN(CPC)) {
				CPC = 0.0;
			}

			if(Double.isNaN(clickPr)) {
				clickPr = 0.0;
			}

			if(Double.isNaN(convProb)) {
				convProb = 0.0;
			}

			if(!Double.isNaN(dailyLimit)) {
				if(numClicks*CPC > dailyLimit) {
					numClicks = (int) (dailyLimit/CPC);
				}
			}

			solutionWeight += numClicks*convProb;
		}

		double originalSolWeight = solutionWeight;

		int numIters = 0;
		while(Math.abs(lastSolWeight-solutionWeight) > threshold) {
			numIters++;
			if(numIters > maxIters) {
				numIters = 0;
				solutionWeight = (_R.nextDouble() + .5) * originalSolWeight; //restart the search
				threshold *= 1.5; //increase the threshold
				maxIters *= 1.25;
			}
			lastSolWeight = solutionWeight;
			solutionWeight = 0;
			double penalty = getPenalty(budget, lastSolWeight);
			for(Query q : _querySpace) {
				if(solution.get(q) == null) {
					continue;
				}
				Predictions predictions = allPredictionsMap.get(q).get(solution.get(q).idx());
				double dailyLimit = Double.NaN;
				if(bidBundle != null) {
					dailyLimit  = bidBundle.getDailyLimit(q);
				}
				double clickPr = predictions.getClickPr();
				double numImps = predictions.getNumImp();
				int numClicks = (int) (clickPr * numImps);
				double CPC = predictions.getCPC();
				double convProb = getConversionPrWithPenalty(q, penalty);

				if(Double.isNaN(CPC)) {
					CPC = 0.0;
				}

				if(Double.isNaN(clickPr)) {
					clickPr = 0.0;
				}

				if(Double.isNaN(convProb)) {
					convProb = 0.0;
				}

				if(!Double.isNaN(dailyLimit)) {
					if(numClicks*CPC > dailyLimit) {
						numClicks = (int) (dailyLimit/CPC);
					}
				}

				solutionWeight += numClicks*convProb;
			}
		}
		return solutionWeight;
	}

	private double solutionWeight(double budget, HashMap<Query, Item> solution, HashMap<Query, ArrayList<Predictions>> allPredictionsMap) {
		return solutionWeight(budget, solution, allPredictionsMap, null);
	}

	private HashMap<Query,Item> fillKnapsackWithCapExt(ArrayList<IncItem> incItems, double budget, HashMap<Query,ArrayList<Predictions>> allPredictionsMap){
		HashMap<Query,Item> solution = new HashMap<Query, Item>();

		int expectedConvs = 0;
		
		for(int i = 0; i < incItems.size(); i++) {
			IncItem ii = incItems.get(i);
			double itemWeight = ii.w();
			double itemValue = ii.v();
			if(budget >= expectedConvs + itemWeight) {
				solution.put(ii.item().q(), ii.item());
				expectedConvs += itemWeight;
			}
			else {
				//				double[] currSolVal = solutionValueMultiDay(solution, budget, allPredictionsMap);
				double[] currSolVal = solutionValueMultiDay2(solution, budget, allPredictionsMap,10);

				HashMap<Query, Item> solutionCopy = (HashMap<Query, Item>) solution.clone();
				solutionCopy.put(ii.item().q(), ii.item());
				//				double[] newSolVal = solutionValueMultiDay(solutionCopy, budget, allPredictionsMap);
				double[] newSolVal = solutionValueMultiDay2(solutionCopy, budget, allPredictionsMap,10);

				//				System.out.println("[" + _day +"] CurrSolVal: " + currSolVal[0] + ", NewSolVal: " + newSolVal[0]);

				if(newSolVal[0] > currSolVal[0]) {
					solution.put(ii.item().q(), ii.item());
					expectedConvs = (int) newSolVal[1];

					if(i != incItems.size() - 1) {
						/*
						 * Discount the item based on the current penalty level
						 */
						double penalty = getPenalty(budget, newSolVal[1]);

						if(FORWARDUPDATING && !PRICELINES) {
							//Update next item
							IncItem nextItem  = incItems.get(i+1);
							double v,w;
							if(nextItem.itemLow() != null) {
								Predictions prediction1 = allPredictionsMap.get(nextItem.item().q()).get(nextItem.itemLow().idx());
								Predictions prediction2 = allPredictionsMap.get(nextItem.item().q()).get(nextItem.itemHigh().idx());
								v = prediction2.getClickPr()*prediction2.getNumImp()*(getConversionPrWithPenalty(nextItem.item().q(), penalty)*_salesPrices.get(nextItem.item().q()) - prediction2.getCPC()) - 
								(prediction1.getClickPr()*prediction1.getNumImp()*(getConversionPrWithPenalty(nextItem.item().q(), penalty)*_salesPrices.get(nextItem.item().q()) - prediction1.getCPC())) ;
								w = prediction2.getClickPr()*prediction2.getNumImp()*getConversionPrWithPenalty(nextItem.item().q(), penalty) - 
								(prediction1.getClickPr()*prediction1.getNumImp()*getConversionPrWithPenalty(nextItem.item().q(), penalty));
							}
							else {
								Predictions prediction = allPredictionsMap.get(nextItem.item().q()).get(nextItem.itemHigh().idx());
								v = prediction.getClickPr()*prediction.getNumImp()*(getConversionPrWithPenalty(nextItem.item().q(), penalty)*_salesPrices.get(nextItem.item().q()) - prediction.getCPC());
								w = prediction.getClickPr()*prediction.getNumImp()*getConversionPrWithPenalty(nextItem.item().q(), penalty);
							}
							IncItem newNextItem = new IncItem(w, v, nextItem.itemHigh(), nextItem.itemLow());
							incItems.remove(i+1);
							incItems.add(i+1, newNextItem);
						}
						else if(PRICELINES) {
							ArrayList<IncItem> updatedItems = new ArrayList<IncItem>();
							for(int j = i+1; j < incItems.size(); j++) {
								IncItem incItem = incItems.get(j);
								Item itemLow = incItem.itemLow();
								Item itemHigh = incItem.itemHigh();

								double newWeight,newValue;

								if(itemLow != null) {
									Predictions prediction1 = allPredictionsMap.get(itemHigh.q()).get(itemLow.idx());
									Predictions prediction2 = allPredictionsMap.get(itemHigh.q()).get(itemHigh.idx());
									newValue = prediction2.getClickPr()*prediction2.getNumImp()*(getConversionPrWithPenalty(incItem.item().q(), penalty)*_salesPrices.get(itemHigh.q()) - prediction2.getCPC()) - 
									(prediction1.getClickPr()*prediction1.getNumImp()*(getConversionPrWithPenalty(incItem.item().q(), penalty)*_salesPrices.get(itemHigh.q()) - prediction1.getCPC())) ;
									newWeight = prediction2.getClickPr()*prediction2.getNumImp()*getConversionPrWithPenalty(incItem.item().q(), penalty) - 
									(prediction1.getClickPr()*prediction1.getNumImp()*getConversionPrWithPenalty(incItem.item().q(), penalty));
								}
								else {
									Predictions prediction = allPredictionsMap.get(itemHigh.q()).get(itemHigh.idx());
									newValue = prediction.getClickPr()*prediction.getNumImp()*(getConversionPrWithPenalty(incItem.item().q(), penalty)*_salesPrices.get(itemHigh.q()) - prediction.getCPC());
									newWeight = prediction.getClickPr()*prediction.getNumImp()*getConversionPrWithPenalty(incItem.item().q(), penalty);
								}
								IncItem newItem = new IncItem(newWeight,newValue,itemHigh, itemLow);
								updatedItems.add(newItem);
							}

							Collections.sort(updatedItems);

							while(incItems.size() > i+1) {
								incItems.remove(incItems.size()-1);
							}
							for(IncItem priceLineItem : updatedItems) {
								incItems.add(incItems.size(),priceLineItem);
							}
						}
					}
				}
				else {
					solution.put(ii.item().q(), ii.item());
					break;
				}
			}
		}
		return solution;
	}

	/**
	 * Get undominated items
	 * @param items
	 * @return
	 */
	public static Item[] getUndominated(Item[] items) {
		Arrays.sort(items,new ItemComparatorByWeight());
		//remove dominated items (higher weight, lower value)		
		ArrayList<Item> temp = new ArrayList<Item>();
		temp.add(items[0]);
		for(int i=1; i<items.length; i++) {
			Item lastUndominated = temp.get(temp.size()-1); 
			if(lastUndominated.v() < items[i].v()) {
				temp.add(items[i]);
			}
		}


		ArrayList<Item> betterTemp = new ArrayList<Item>();
		betterTemp.addAll(temp);
		for(int i = 0; i < temp.size(); i++) {
			ArrayList<Item> duplicates = new ArrayList<Item>();
			Item item = temp.get(i);
			duplicates.add(item);
			for(int j = i + 1; j < temp.size(); j++) {
				Item otherItem = temp.get(j);
				if(item.v() == otherItem.v() && item.w() == otherItem.w()) {
					duplicates.add(otherItem);
				}
			}
			if(duplicates.size() > 1) {
				betterTemp.removeAll(duplicates);
				double minBid = 10;
				double maxBid = -10;
				for(int j = 0; j < duplicates.size(); j++) {
					double bid = duplicates.get(j).b();
					if(bid > maxBid) {
						maxBid = bid;
					}
					if(bid < minBid) {
						minBid = bid;
					}
				}
				Item newItem = new Item(item.q(), item.w(), item.v(), (maxBid+minBid)/2.0, item.targ(), item.isID(),item.idx());
				betterTemp.add(newItem);
			}
		}

		//items now contain only undominated items
		items = betterTemp.toArray(new Item[0]);
		Arrays.sort(items,new ItemComparatorByWeight());

		//remove lp-dominated items
		ArrayList<Item> q = new ArrayList<Item>();
		q.add(new Item(new Query(),0,0,-1,false,1,0));//add item with zero weight and value

		for(int i=0; i<items.length; i++) {
			q.add(items[i]);//has at least 2 items now
			int l = q.size()-1;
			Item li = q.get(l);//last item
			Item nli = q.get(l-1);//next to last
			if(li.w() == nli.w()) {
				if(li.v() > nli.v()) {
					q.remove(l-1);
				}else{
					q.remove(l);
				}
			}
			l = q.size()-1; //reset in case an item was removed
			//while there are at least three elements and ...
			while(l > 1 && (q.get(l-1).v() - q.get(l-2).v())/(q.get(l-1).w() - q.get(l-2).w()) 
					<= (q.get(l).v() - q.get(l-1).v())/(q.get(l).w() - q.get(l-1).w())) {
				q.remove(l-1);
				l--;
			}
		}

		//remove the (0,0) item
		if(q.get(0).w() == 0 && q.get(0).v() == 0) {
			q.remove(0);
		}

		Item[] uItems = (Item[]) q.toArray(new Item[0]);
		return uItems;
	}


	/**
	 * Get incremental items
	 * @param items
	 * @return
	 */
	public IncItem[] getIncremental(Item[] items) {
		debug("PRE INCREMENTAL");
		for(int i = 0; i < items.length; i++) {
			debug("\t" + items[i]);
		}

		Item[] uItems = getUndominated(items);

		debug("UNDOMINATED");
		for(int i = 0; i < uItems.length; i++) {
			debug("\t" + uItems[i]);
		}

		IncItem[] ii = new IncItem[uItems.length];

		if (uItems.length != 0){ //getUndominated can return an empty array
			ii[0] = new IncItem(uItems[0].w(), uItems[0].v(), uItems[0], null);
			for(int item=1; item<uItems.length; item++) {
				Item prev = uItems[item-1];
				Item cur = uItems[item];
				ii[item] = new IncItem(cur.w() - prev.w(), cur.v() - prev.v(), cur, prev);
			}
		}
		debug("INCREMENTAL");
		for(int i = 0; i < ii.length; i++) {
			debug("\t" + ii[i]);
		}
		return ii;
	}

	public double getConversionPrWithPenalty(Query q, double penalty) {
		double convPr;
		String component = q.getComponent();
		if(_compSpecialty.equals(component)) {
			convPr = eta(_convPrModel.getPrediction(q)*penalty,1+_CSB);
		}
		else if(component == null) {
			convPr = eta(_convPrModel.getPrediction(q)*penalty,1+_CSB) * (1/3.0) + _convPrModel.getPrediction(q)*penalty*(2/3.0);
		}
		else {
			convPr = _convPrModel.getPrediction(q)*penalty;
		}
		return convPr;
	}

	private double randDouble(double a, double b) {
		double rand = _R.nextDouble();
		return rand * (b - a) + a;
	}

	public void debug(Object str) {
		if(DEBUG) {
			System.out.println(str);
		}
	}

	@Override
	public String toString() {
		return "SemiEndoMCKPBid(Budget: " + BUDGET + ", Forward Update: " + FORWARDUPDATING + ", Pricelines: " + PRICELINES + ")";
	}

	@Override
	public AbstractAgent getCopy() {
		return new SemiEndoMCKPBid(FORWARDUPDATING,PRICELINES,BUDGET);
	}
}