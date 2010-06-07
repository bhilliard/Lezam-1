package agents.modelbased;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

import models.AbstractModel;
import models.bidtocpc.AbstractBidToCPC;
import models.bidtocpc.EnsembleBidToCPC;
import models.bidtocpc.RegressionBidToCPC;
import models.bidtocpc.WEKAEnsembleBidToCPC;
import models.bidtoprclick.AbstractBidToPrClick;
import models.bidtoprclick.EnsembleBidToPrClick;
import models.bidtoprclick.RegressionBidToPrClick;
import models.bidtoprclick.WEKAEnsembleBidToPrClick;
import models.postoprclick.RegressionPosToPrClick;
import models.prconv.AbstractConversionModel;
import models.prconv.GoodConversionPrModel;
import models.prconv.HistoricPrConversionModel;
import models.querytonumimp.AbstractQueryToNumImp;
import models.querytonumimp.BasicQueryToNumImp;
import models.sales.SalesDistributionModel;
import models.targeting.BasicTargetModel;
import models.unitssold.AbstractUnitsSoldModel;
import models.unitssold.BasicUnitsSoldModel;
import models.unitssold.UnitsSoldMovingAvg;
import models.usermodel.AbstractUserModel;
import models.usermodel.BasicUserModel;
import agents.AbstractAgent;
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
public class MCKPBidNoDomElim extends AbstractAgent {

	private static final int MAX_TIME_HORIZON = 5;
	private static final boolean TARGET = false;
	private static final boolean BUDGET = false;
	private static final boolean SAFETYBUDGET = true;
	private static final boolean BOOST = false;

	private double _safetyBudget = 800;

	//Days since Last Boost
	private double lastBoost;
	private double boostCoeff = 1.2;

	private Random _R = new Random();
	private boolean DEBUG = false;
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
	private Hashtable<Query, Integer> _queryId;
	private LinkedList<Double> bidList;
	private int lagDays = 5;
	private boolean salesDistFlag;

	public MCKPBidNoDomElim() {
		_R.setSeed(124962748);
		bidList = new LinkedList<Double>();
		//		double increment = .25;
		double increment  = .04;
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
		GoodConversionPrModel convPrModel = new GoodConversionPrModel(_querySpace,basicTargModel);
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

			String component = q.getComponent();
			if(_compSpecialty.equals(component)) {
				_baseConvProbs.put(q,eta(_baseConvProbs.get(q),1+_CSB));
			}
			else if(component == null) {
				_baseConvProbs.put(q,eta(_baseConvProbs.get(q),1+_CSB)*(1/3.0) + _baseConvProbs.get(q)*(2/3.0));
			}
		}

		_queryId = new Hashtable<Query,Integer>();
		int i = 0;
		for(Query q : _querySpace){
			i++;
			_queryId.put(q, i);
		}

		lastBoost = 5;
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
			double budget;
			if(_day < 4) {
				budget = _capacity/_capWindow;
			}
			else {
				//				budget = Math.max(20,_capacity*(2.0/5.0) - _unitsSold.getWindowSold()/4);
				budget = Math.max(_capacity/((double)_capWindow)*(1/3.0),_capacity - _unitsSold.getWindowSold());
				debug("Unit Sold Model Budget "  +budget);
			}

			if(BOOST) {
				if(lastBoost >= 3 && (_unitsSold.getThreeDaysSold() < (_capacity * (3.0/5.0)))) {
					debug("\n\nBOOOOOOOOOOOOOOOOOOOST\n\n");
					lastBoost = -1;
					budget *= boostCoeff;
				}
				lastBoost++;
			}
			double penalty = 1.0;
			if(budget < 0) {
				penalty = Math.pow(_lambda, Math.abs(budget));
			}
			//NEED TO USE THE MODELS WE ARE PASSED!!!
			HashMap<Query,HashMap<Integer,WeightValuePair>> wvMap = new HashMap<Query, HashMap<Integer,WeightValuePair>>();
			for(Query q : _querySpace) {
				HashMap<Integer,WeightValuePair> wvQueryMap = new HashMap<Integer, WeightValuePair>();
				debug("Query: " + q);
				for(int i = 0; i < bidList.size(); i++) {
					double salesPrice = _salesPrices.get(q);
					double bid = bidList.get(i);
					double clickPr = _bidToPrClick.getPrediction(q, bid, new Ad());
					double numImps = _queryToNumImpModel.getPrediction(q,(int) (_day+1));
					int numClicks = (int) (clickPr * numImps);
					double CPC = _bidToCPC.getPrediction(q, bid);
					double convProb = _convPrModel.getPrediction(q)*penalty;

					if(Double.isNaN(CPC)) {
						CPC = 0.0;
					}

					if(Double.isNaN(clickPr)) {
						clickPr = 0.0;
					}

					if(Double.isNaN(convProb)) {
						convProb = 0.0;
					}
					double w = numClicks*convProb;				//weight = numClciks * convProv
					double v = numClicks*convProb*salesPrice - numClicks*CPC;	//value = revenue - cost	[profit]
					wvQueryMap.put(i, new WeightValuePair(w,v));
				}
				wvMap.put(q, wvQueryMap);
			}

			HashMap<Query,Integer> solution = new HashMap<Query, Integer>();
			for(Query q : _querySpace) {
				solution.put(q, 0);
			}

			HashMap<Query,Double> lastEffDiff = new HashMap<Query, Double>();
			double numOverCap = 0;
			while(true) {
				Query bestQ = null;
				double bestEffDiff = 0;
				for(Query query : _querySpace) {
					int currentSol = solution.get(query);
					if(currentSol !=  bidList.size()-1) { 
						Double effDiff = lastEffDiff.get(query);
						if(effDiff == null) {
							HashMap<Integer, WeightValuePair> wvQueryMap = wvMap.get(query);
							WeightValuePair wvLow = wvQueryMap.get(currentSol);
							WeightValuePair wvHigh = wvQueryMap.get(currentSol+1);
							if(wvHigh.getValue() > 0) {
								effDiff = (wvHigh.getValue()-wvLow.getValue())/(wvHigh.getWeight() - wvLow.getWeight());
							}
							else {
								effDiff = 0.0;
							}
							lastEffDiff.put(query, effDiff);
						}
						if(effDiff > bestEffDiff) {
							bestEffDiff = effDiff;
							bestQ = query;
						}
					}
				}
				if(bestQ == null) {
					break;
				}
				double totCapUsed = 0;
				for(Query q : _querySpace) {
					totCapUsed += wvMap.get(q).get(solution.get(q)).getWeight();
				}
				if(totCapUsed > budget) {
					double min = numOverCap;
					numOverCap = totCapUsed + wvMap.get(bestQ).get(solution.get(bestQ)).getWeight() - budget;
					double max = numOverCap;

					double avgConvProb = 0; //the average probability of conversion;
					for(Query q : _querySpace) {
						if(_day < 2) {
							avgConvProb += _baseConvProbs.get(q) / 16.0;
						}
						else {
							avgConvProb += _baseConvProbs.get(q) * _salesDist.getPrediction(q);
						}
					}

					double avgUSP = 0;
					for(Query q : _querySpace) {
						if(_day < 2) {
							avgUSP += _salesPrices.get(q) / 16.0;
						}
						else {
							avgUSP += _salesPrices.get(q) * _salesDist.getPrediction(q);
						}
					}

					double valueLostWindow = Math.max(1, Math.min(_capWindow, 59 - _day));
					double valueLost = 0;
					for (double j = min+1; j <= max; j++){
						double iD = Math.pow(_lambda, j);
						double worseConvProb = avgConvProb*iD; //this is a gross average that lacks detail
						valueLost += (avgConvProb - worseConvProb)*avgUSP*valueLostWindow; //You also lose conversions in the future (for 5 days)
					}

					if(wvMap.get(bestQ).get(solution.get(bestQ)).getValue() > valueLost) {
						solution.put(bestQ, solution.get(bestQ)+1);
						lastEffDiff.put(bestQ, null);
					}
					else {
						break;
					}
				}
				else {
					solution.put(bestQ, solution.get(bestQ)+1);
					lastEffDiff.put(bestQ, null);
				}
			}

			//set bids
			for(Query q : _querySpace) {
				if(solution.get(q) > 0) {
					bidBundle.addQuery(q, bidList.get(solution.get(q)), new Ad());
				}
				else {
					double bid;
					if (q.getType().equals(QueryType.FOCUS_LEVEL_ZERO))
						bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);
					else if (q.getType().equals(QueryType.FOCUS_LEVEL_ONE))
						bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);
					else
						bid = randDouble(.04,_salesPrices.get(q) * _baseConvProbs.get(q) * _baseClickProbs.get(q) * .8);

					//					System.out.println("Exploring " + q + "   bid: " + bid);
					bidBundle.addQuery(q, bid, new Ad(), bid*5);
				}
			}
			/*
			 * Pass expected conversions to unit sales model
			 */
			double threshold = 2;
			double lastSolWeight = 0.0;
			double solutionWeight = threshold+1;
			int numIters = 0;
			while(Math.abs(lastSolWeight-solutionWeight) > threshold) {
				numIters++;
				lastSolWeight = solutionWeight;
				solutionWeight = 0;
				double newPenalty;
				double newNumOverCap = lastSolWeight - budget;
				if(budget < 0) {
					newPenalty = 0.0;
					int num = 0;
					for(double j = Math.abs(budget)+1; j <= newNumOverCap; j++) {
						newPenalty += Math.pow(_lambda, j);
						num++;
					}
					newPenalty /= (num);
					double oldPenalty = Math.pow(_lambda, Math.abs(budget));
					newPenalty = newPenalty/oldPenalty;
				}
				else {
					if(newNumOverCap <= 0) {
						newPenalty = 1.0;
					}
					else {
						newPenalty = budget;
						for(int j = 1; j <= newNumOverCap; j++) {
							newPenalty += Math.pow(_lambda, j);
						}
						newPenalty /= (budget + newNumOverCap);
					}
				}
				if(Double.isNaN(newPenalty)) {
					newPenalty = 1.0;
				}
				for(Query q : _querySpace) {
					double bid = bidBundle.getBid(q);
					double dailyLimit = bidBundle.getDailyLimit(q);
					double clickPr = _bidToPrClick.getPrediction(q, bid, new Ad());
					double numImps = _queryToNumImpModel.getPrediction(q,(int) (_day+1));
					int numClicks = (int) (clickPr * numImps);
					double CPC = _bidToCPC.getPrediction(q, bid);
					double convProb = _convPrModel.getPrediction(q)*newPenalty;

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
//			System.out.println(numIters);
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
		System.out.println("This took " + (elapsed / 1000) + " seconds");
		return bidBundle;
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
		return "MCKPBidNoDomElim";
	}

	@Override
	public AbstractAgent getCopy() {
		return new MCKPBidNoDomElim();
	}

	public class WeightValuePair {
		private double _weight;
		private double _value;

		public WeightValuePair(double weight, double value) {
			_weight = weight;
			_value = value;
		}

		public double getValue() {
			return _value;
		}

		public void setValue(double _value) {
			this._value = _value;
		}

		public double getWeight() {
			return _weight;
		}

		public void setWeight(double _weight) {
			this._weight = _weight;
		}
	}

}