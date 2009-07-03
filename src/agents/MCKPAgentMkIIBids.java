/**
 * 
 */
package agents;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;

import newmodels.AbstractModel;
import newmodels.bidtocpc.AbstractBidToCPC;
import newmodels.bidtocpc.RegressionBidToCPC;
import newmodels.bidtoprclick.AbstractBidToPrClick;
import newmodels.bidtoprclick.RegressionBidToPrClick;
import newmodels.prconv.GoodConversionPrModel;
import newmodels.prconv.NewAbstractConversionModel;
import newmodels.querytonumimp.AbstractQueryToNumImp;
import newmodels.querytonumimp.BasicQueryToNumImp;
import newmodels.unitssold.AbstractUnitsSoldModel;
import newmodels.unitssold.UnitsSoldMovingAvg;
import newmodels.usermodel.AbstractUserModel;
import newmodels.usermodel.BasicUserModel;
import agents.mckp.IncItem;
import agents.mckp.Item;
import agents.mckp.ItemComparatorByWeight;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.QueryType;
import edu.umich.eecs.tac.props.SalesReport;

/**
 * @author jberg
 *
 */
public class MCKPAgentMkIIBids extends SimAbstractAgent {

	private static final int MAX_TIME_HORIZON = 5;

	private Random _R = new Random();
	private boolean DEBUG = false;
	private double LAMBDA = .995;
	private int _numUsers = 90000;
	private HashMap<Query, Double> _salesPrices;
	private HashMap<Query, Double> _baseConvProbs;
	private AbstractUserModel _userModel;
	private AbstractQueryToNumImp _queryToNumImpModel;
	private AbstractBidToCPC _bidToCPC;
	private AbstractBidToPrClick _bidToPrClick;
	private AbstractUnitsSoldModel _unitsSold;
	private NewAbstractConversionModel _convPrModel;
	private Hashtable<Query, Integer> _queryId;
	private LinkedList<Double> bidList;
	private int _capacityInc = 10;
	private int lagDays = 5;


	/*
	 * For error calculations
	 */
	private LinkedList<HashMap<Query, Double>> CPCPredictions, ClickPrPredictions;
	private double sumCPCError, sumClickPrError;
	private int errorDayCounter;

	public MCKPAgentMkIIBids() {
		bidList = new LinkedList<Double>();
		//		double increment = .25;
		double increment  = .15;
		double min = .15;
		double max = 2;
		int tot = (int) Math.ceil((max-min) / increment);
		for(int i = 0; i < tot; i++) {
			bidList.add(min+(i*increment));
		}
		CPCPredictions = new LinkedList<HashMap<Query,Double>>();
		ClickPrPredictions = new LinkedList<HashMap<Query,Double>>();
		sumCPCError = 0.0;
		sumClickPrError = 0.0;
		errorDayCounter = 0;
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
		AbstractBidToCPC bidToCPC = new RegressionBidToCPC(_querySpace);
		AbstractBidToPrClick bidToPrClick = new RegressionBidToPrClick(_querySpace);
		AbstractUnitsSoldModel unitsSold = new UnitsSoldMovingAvg(_querySpace,_capacity,_capWindow);
		NewAbstractConversionModel convPrModel = new GoodConversionPrModel(_querySpace);
		models.add(userModel);
		models.add(queryToNumImp);
		models.add(bidToCPC);
		models.add(bidToPrClick);
		models.add(unitsSold);
		models.add(convPrModel);
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
			else if(model instanceof NewAbstractConversionModel) {
				NewAbstractConversionModel convPrModel = (NewAbstractConversionModel) model;
				_convPrModel = convPrModel;
			}
			else {
				//				throw new RuntimeException("Unhandled Model (you probably would have gotten a null pointer later)"+model);
			}
		}
	}

	@Override
	public void initBidder() {

		_baseConvProbs = new HashMap<Query, Double>();

		// set revenue prices
		_salesPrices = new HashMap<Query,Double>();
		for(Query q : _querySpace) {

			String manufacturer = q.getManufacturer();
			if(manufacturer == _manSpecialty) {
				_salesPrices.put(q, 15.0);
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

			String component = q.getComponent();
			if(component == _compSpecialty) {
				_baseConvProbs.put(q,eta(_baseConvProbs.get(q),1+_CSB));
			}
		}
		_queryId = new Hashtable<Query,Integer>();
		int i = 0;
		for(Query q : _querySpace){
			i++;
			_queryId.put(q, i);
		}
	}


	@Override
	public void updateModels(SalesReport salesReport, QueryReport queryReport) {

		for(AbstractModel model:_models) {
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
				bidToCPC.updateModel(queryReport, _bidBundles.get(_bidBundles.size()-2));
			}
			else if(model instanceof AbstractBidToPrClick) {
				AbstractBidToPrClick bidToPrClick = (AbstractBidToPrClick) model;
				bidToPrClick.updateModel(queryReport, _bidBundles.get(_bidBundles.size()-2));
			}
			else if(model instanceof NewAbstractConversionModel) {
				NewAbstractConversionModel convPrModel = (NewAbstractConversionModel) model;
				int timeHorizon = (int) Math.min(Math.max(1,_day - 1), MAX_TIME_HORIZON);
				if(model instanceof GoodConversionPrModel) {
					GoodConversionPrModel adMaxModel = (GoodConversionPrModel) convPrModel;
					adMaxModel.setTimeHorizon(timeHorizon);
				}
				convPrModel.updateModel(queryReport, salesReport);
			}
			else {
				throw new RuntimeException("Unhandled Model (you probably would have gotten a null pointer later)");
			}
		}
	}


	@Override
	public BidBundle getBidBundle(Set<AbstractModel> models) {
		BidBundle bidBundle = new BidBundle();
		double numIncItemsPerSet = 0;
		if(_day > lagDays){
			buildMaps(models);
			//NEED TO USE THE MODELS WE ARE PASSED!!!

			HashMap<Query,Double> dailyCPCPredictions = new HashMap<Query, Double>();
			HashMap<Query,Double> dailyClickPrPredictions = new HashMap<Query, Double>();
			LinkedList<IncItem> allIncItems = new LinkedList<IncItem>();

			//want the queries to be in a guaranteed order - put them in an array
			//index will be used as the id of the query
			for(Query q : _querySpace) {
				double salesPrice = _salesPrices.get(q);

				LinkedList<Item> itemList = new LinkedList<Item>();
				System.out.println("Query: " + q);
				for(int i = 0; i < bidList.size(); i++) {
					double bid = bidList.get(i);
					double clickPr = _bidToPrClick.getPrediction(q, bid, new Ad(), _bidBundles.getLast());
					double numImps = _queryToNumImpModel.getPrediction(q);
					int numClicks = (int) (clickPr * numImps);
					double CPC = _bidToCPC.getPrediction(q, bid, _bidBundles.getLast());

					System.out.println("\tBid: " + bid);
					System.out.println("\tCPC: " + CPC);
					debug("\tClickPr: " + clickPr);
					debug("\tNumImps: " + numImps);
					debug("\tMumClicks: " + numClicks);


					double convProb = _convPrModel.getPrediction(q);
					System.out.println(convProb);

					double w = numClicks*convProb;				//weight = numClciks * convProv
					double v = numClicks*convProb*salesPrice - numClicks*CPC;	//value = revenue - cost	[profit]

					int isID = _queryId.get(q);
					itemList.add(new Item(q,w,v,bid,isID));	
				}
				debug("Items for " + q);
				Item[] items = itemList.toArray(new Item[0]);
				IncItem[] iItems = getIncremental(items);
				numIncItemsPerSet += iItems.length;
				allIncItems.addAll(Arrays.asList(iItems));
			}
			numIncItemsPerSet /= 16.0;
			debug(numIncItemsPerSet);
			double budget = _capacity/_capWindow;
			if(_day < 4) {
				//do nothing
			}
			else {
				if(_unitsSold != null) {
					debug("Average Budget: " + budget);
					budget = _capacity*(2.0/5.0) - _unitsSold.getWindowSold()/4;
					if(budget < 20) {
						budget = 20;
					}
					debug("Unit Sold Model Budget "  +budget);
				}
			}

			Collections.sort(allIncItems);
			//			Misc.printList(allIncItems,"\n", Output.OPTIMAL);

			//			HashMap<Integer,Item> solution = fillKnapsack(allIncItems, budget);
			HashMap<Integer,Item> solution = fillKnapsackWithCapExt(allIncItems, budget);

			//set bids
			for(Query q : _querySpace){

				Integer isID = _queryId.get(q);
				double bid;

				if(solution.containsKey(isID)) {
					bid = solution.get(isID).b();
				}
				else bid = 0; // TODO this is a hack that was the result of the fact that the item sets were empty

				bid *= randDouble(.9,1.1);  //Mult by rand to avoid users learning patterns.
				
				dailyCPCPredictions.put(q, _bidToCPC.getPrediction(q, bid, _bidBundles.getLast()));
				dailyClickPrPredictions.put(q, _bidToPrClick.getPrediction(q, bid, new Ad(), _bidBundles.getLast()));

				bidBundle.addQuery(q, bid, new Ad(), Double.NaN);
			}
			CPCPredictions.add(dailyCPCPredictions);
			ClickPrPredictions.add(dailyClickPrPredictions);
			/*
			 * Update model error
			 */
			if(_day > lagDays+3) {
				errorDayCounter++;
				/*
				 * CPC Error
				 */
				HashMap<Query, Double> cpcpredictions = CPCPredictions.get(CPCPredictions.size()-3);
				QueryReport queryReport = _queryReports.getLast();
				double dailyCPCerror = 0;
				for(Query query : _querySpace) {
					if (Double.isNaN(queryReport.getCPC(query))) {
						//If CPC is NaN it means it is zero, which means our entire prediction is error!
						double error = cpcpredictions.get(query)*cpcpredictions.get(query);
						dailyCPCerror += error;
						sumCPCError += error;
					}
					else {
						double error = (queryReport.getCPC(query) - cpcpredictions.get(query))*(queryReport.getCPC(query) - cpcpredictions.get(query));
						dailyCPCerror += error;
						sumCPCError += error;
					}
				}
				System.out.println(errorDayCounter);
				double stddevCPC = Math.sqrt(sumCPCError/(errorDayCounter*16));
				System.out.println("Daily CPC Error: " + Math.sqrt(dailyCPCerror/16));
				System.out.println("CPC  Standard Deviation: " + stddevCPC);
				
				/*
				 * ClickPr Error
				 */
				HashMap<Query, Double> clickprpredictions = ClickPrPredictions.get(ClickPrPredictions.size()-3);
				double dailyclickprerror = 0;
				for(Query query : _querySpace) {
					double clicks = 0;
					double imps = 0;
					if (clicks == 0 || imps == 0) {
						//If CPC is NaN it means it is zero, which means our entire prediction is error!
						double error = clickprpredictions.get(query)*clickprpredictions.get(query);
						dailyclickprerror += error;
						sumClickPrError += error;
					}
					else {
						double error = (clicks/imps - clickprpredictions.get(query))*(clicks/imps- clickprpredictions.get(query));
						dailyclickprerror = error;
						sumClickPrError += error;
					}
				}
				double stddevClickPr = Math.sqrt(sumClickPrError/(errorDayCounter*16));
				System.out.println("Daily ClickPr Error: " + Math.sqrt(dailyclickprerror/16));
				System.out.println("ClickPr Standard Deviation: " + stddevClickPr);
			}

		}
		else {
			for(Query q : _querySpace){
				double bid;
				if (q.getType().equals(QueryType.FOCUS_LEVEL_ZERO))
					bid = randDouble(.1,.6);
				else if (q.getType().equals(QueryType.FOCUS_LEVEL_ONE))
					bid = randDouble(.25,.75);
				else 
					bid = randDouble(.35,1.0);
				bidBundle.addQuery(q, bid, new Ad(), Double.NaN);
			}
		}

		return bidBundle;
	}


	/**
	 * Greedily fill the knapsack by selecting incremental items
	 * @param incItems
	 * @param budget
	 * @return
	 */
	private HashMap<Integer,Item> fillKnapsack(LinkedList<IncItem> incItems, double budget){
		HashMap<Integer,Item> solution = new HashMap<Integer, Item>();
		for(IncItem ii: incItems) {
			//lower efficiencies correspond to heavier items, i.e. heavier items from the same item
			//set replace lighter items as we want
			if(budget >= 0) {
				//				debug("adding item " + ii);
				solution.put(ii.item().isID(), ii.item());
				budget -= ii.w();
			}
			else {
				break;
			}
		}
		return solution;
	}

	private HashMap<Integer,Item> fillKnapsackWithCapExt(LinkedList<IncItem> incItems, double budget){
		HashMap<Integer,Item> solution = new HashMap<Integer, Item>();
		LinkedList<IncItem> temp = new LinkedList<IncItem>();

		boolean incremented = false;
		double valueLost = 0;
		double valueGained = 0;
		int knapSackIter = 0;

		for(IncItem ii: incItems) {
			//lower efficiencies correspond to heavier items, i.e. heavier items from the same item
			//set replace lighter items as we want
			//			if(budget >= ii.w()) {
			if(budget >= 0) {
				if (incremented) {
					temp.addLast(ii);
					budget -= ii.w();
					System.out.println("Temporarily adding: " + ii);
					valueGained += ii.v(); //amount gained as a result of extending capacity
				}
				else {
					System.out.println("adding item" + ii);
					solution.put(ii.item().isID(), ii.item());
					budget -= ii.w();
				}
			}
			else{
				if (incremented) {
					if (valueGained >= valueLost) { //checks to see if it was worth extending our capacity
						while (!temp.isEmpty()){
							IncItem inc = temp.poll();
							System.out.println("adding item over capacity " + inc);
							solution.put(inc.item().isID(), inc.item());
						}
						valueLost = 0;
						valueGained = 0;
					}
					else {
						System.out.println("Not worth overselling anymore");
						break;
					}
				}
				double avgConvProb = .253; //the average probability of conversion;
				/*
				double avgUSP = 0;
				for (Query q : _querySpace){
					avgUSP += _rpc.get(q);
				}
				avgUSP /= 16;
				 */// This can be used later if the values actually change for the sales bonus
				double avgUSP = 11.25;
				for (int i = _capacityInc*knapSackIter+1; i <= _capacityInc*(knapSackIter+1); i++){
					double iD = Math.pow(LAMBDA, i);
					double worseConvProb = avgConvProb*iD; //this is a gross average that lacks detail
					valueLost += (avgConvProb - worseConvProb)*avgUSP;
					System.out.println("Adding " + ((avgConvProb - worseConvProb)*avgUSP) + " to value lost");
				}
				System.out.println("Total value lost: " + valueLost);
				budget+=_capacityInc;
				incremented = true;
				knapSackIter++;
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
		LinkedList<Item> temp = new LinkedList<Item>();
		temp.add(items[0]);
		for(int i=1; i<items.length; i++) {
			Item lastUndominated = temp.get(temp.size()-1); 
			if(lastUndominated.v() < items[i].v()) {
				temp.add(items[i]);
			}
		}

		//items now contain only undominated items
		items = temp.toArray(new Item[0]);

		//remove lp-dominated items
		LinkedList<Item> q = new LinkedList<Item>();
		q.add(new Item(new Query(),0,0,-1,1));//add item with zero weight and value

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
			ii[0] = new IncItem(uItems[0].w(), uItems[0].v(), uItems[0]);
			for(int item=1; item<uItems.length; item++) {
				Item prev = uItems[item-1];
				Item cur = uItems[item];
				ii[item] = new IncItem(cur.w() - prev.w(), cur.v() - prev.v(), cur);
			}
		}
		debug("INCREMENTAL");
		for(int i = 0; i < ii.length; i++) {
			debug("\t" + ii[i]);
		}
		return ii;
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

}
