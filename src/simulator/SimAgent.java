package simulator;

import java.util.HashMap;
import java.util.Set;

import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.SalesReport;

public class SimAgent {
	
	
	private HashMap<Query, Double> _bids;
	private HashMap<Query, Double> _budgets;
	private double _totBudget;
	private HashMap<Query, Double> _advEffect;
	private HashMap<Query, Ad> _adType;
	private int _oneDayCap;
	private String _manSpecialty;
	private String _compSpecialty;
	private double _squashing;
	private Set<Query> _querySpace;
	
	private HashMap<Query, Double> _CPC;
	private HashMap<Query, Double> _cost;
	private HashMap<Query, Double> _revenue;
	private HashMap<Query, Integer> _unitsSold;
	private HashMap<Query, Integer> _numClicks;
	private HashMap<Query, Integer> _regImps;
	private HashMap<Query, Integer> _promImps;
	private HashMap<Query, Double> _posSum;
	private int _totUnitsSold;
	private int _totClicks;
	private double _totCost;
	private double _totRevenue;
	private String _advId;

	public SimAgent(HashMap<Query,Double> bids,
					HashMap<Query,Double> budgets,
					double totBudget,
					HashMap<Query,Double> advEffect,
					HashMap<Query,Ad> adType,
					int oneDayCap,
					String manSpecialty,
					String compSpecialty,
					String advId,
					double squashing,
					Set<Query> querySpace) {
		_bids = bids;
		_budgets = budgets;
		_totBudget = totBudget;
		_advEffect = advEffect;
		_adType = adType;
		_oneDayCap = oneDayCap;
		_manSpecialty = manSpecialty;
		_compSpecialty = compSpecialty;
		_advId = advId;
		_squashing = squashing;
		_querySpace = querySpace;
		
		_CPC = new HashMap<Query, Double>();
		_cost = new HashMap<Query, Double>();
		_revenue = new HashMap<Query, Double>();
		_unitsSold = new HashMap<Query, Integer>();
		_numClicks = new HashMap<Query, Integer>();
		_regImps = new HashMap<Query, Integer>();
		_promImps = new HashMap<Query, Integer>();
		_posSum = new HashMap<Query, Double>();
		_totUnitsSold = 0;
		_totClicks = 0;
		_totCost = 0.0;
		_totRevenue = 0.0;
		for(Query query : _querySpace) {
			_CPC.put(query, 0.0);
			_cost.put(query, 0.0);
			_revenue.put(query, 0.0);
			_unitsSold.put(query, 0);
			_numClicks.put(query,0);
			_regImps.put(query,0);
			_promImps.put(query,0);
			_posSum.put(query, 0.0);
		}
	}
	
	public double getSquashedBid(Query query) {
		return Math.pow(_advEffect.get(query), _squashing) * _bids.get(query);
	}
	
	public double getBid(Query query) {
		return _bids.get(query);
	}
	
	public double getAdvEffect(Query query) {
		return _advEffect.get(query);
	}
	
	public String getManSpecialty() {
		return _manSpecialty;
	}
	
	public String getCompSpecialty() {
		return _compSpecialty;
	}

	public double getBudget(Query query) {
		return _budgets.get(query);
	}

	public double getTotBudget() {
		return _totBudget;
	}
	
	public Ad getAd(Query query) {
		return _adType.get(query);
	}
	
	public double getCPC(Query query) {
		return _CPC.get(query);
	}

	public void setCPC(Query query, double cpc) {
		_CPC.put(query, cpc);
	}
	
	public double getCost(Query query) {
		return _cost.get(query);
	}
	
	public void setCost(Query query, double cost) {
		_cost.put(query, cost);
	}
	
	public void addCost(Query query, double cost) {
		_cost.put(query, _cost.get(query) + cost);
		_totCost += cost;
		_totClicks++;
		_numClicks.put(query, _numClicks.get(query) + 1);
	}
	
	public double getRevenue(Query query) {
		return _revenue.get(query);
	}
	
	public void setRevenue(Query query, double revenue) {
		_revenue.put(query, revenue);
	}
	
	public void addRevenue(Query query, double revenue) {
		_revenue.put(query, _revenue.get(query) + revenue);
		_totRevenue += revenue;
		addUnitSold(query);
	}
	
	public double getUnitsSold(Query query) {
		return _unitsSold.get(query);
	}
	
	public void setUnitsSold(Query query, int unitsSold) {
		_unitsSold.put(query, unitsSold);
	}
	
	public void addUnitSold(Query query) {
		_unitsSold.put(query, _unitsSold.get(query) + 1);
		_totUnitsSold++;
	}

	public void addImpressions(Query query, int regImps, int promImps, int pos) {
		_regImps.put(query,_regImps.get(query) + regImps);
		_promImps.put(query, _promImps.get(query) + promImps);
		_posSum.put(query, _posSum.get(query) + pos);
	}
	
	public int getOverCap() {
		if(_totUnitsSold > _oneDayCap) {
			return _totUnitsSold - _oneDayCap;
		}
		else {
			return 0;
		}
	}
	
	public double getTotCost() {
		return _totCost;
	}
	
	public double getTotRevenue() {
		return _totRevenue;
	}
	
	public double getTotUnitsSold() {
		return _totUnitsSold;
	}
	
	public String getAdvId() {
		return _advId;
	}
	
	public int getNumClicks(Query query) {
		return _numClicks.get(query);
	}
	
	public int getNumPromImps(Query query) {
		return _promImps.get(query);
	}
	
	public int getNumRegImps(Query query) {
		return _regImps.get(query);
	}
	
	public double getPosSum(Query query) {
		return _posSum.get(query);
	}
	
	public QueryReport buildQueryReport() {
		QueryReport queryReport = new QueryReport();
		for(Query query : _querySpace) {
			queryReport.addQuery(query,_regImps.get(query),_promImps.get(query),_numClicks.get(query),_cost.get(query),_posSum.get(query));
			queryReport.setAd(query, _adType.get(query));
		}
		return queryReport;
	}
	
	public SalesReport buildSalesReport() {
		SalesReport salesReport = new SalesReport();
		for(Query query : _querySpace) {
			salesReport.addQuery(query);
			salesReport.addConversions(query,_unitsSold.get(query));
			salesReport.addRevenue(query,_revenue.get(query));
		}
		return salesReport;
	}
	
}