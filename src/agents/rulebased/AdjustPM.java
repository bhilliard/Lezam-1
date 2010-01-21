package agents.rulebased;

import java.util.HashMap;
import java.util.Set;

import agents.AbstractAgent;

import models.AbstractModel;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Product;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryType;

public class AdjustPM extends RuleBasedAgent {

	protected BidBundle _bidBundle;
	protected HashMap<Query, Double> _salesDistribution;
	protected final boolean TARGET = false;
	protected final boolean BUDGET = false;
	protected final boolean DAILYBUDGET = false;
	protected double _alphaIncTS;
	protected double _betaIncTS;
	protected double _alphaDecTS;
	protected double _betaDecTS;
	protected double _alphaIncPM;
	protected double _betaIncPM;
	protected double _alphaDecPM;
	protected double _betaDecPM;
	protected double _initPM;
	protected HashMap<Query, Double> _PM;
	
	public AdjustPM() {
		this(-0.0060,-0.3,0.0070,-0.13333499999999998,0.9000000000000002,0.0,0.23332800000000004,-0.0010,0.13332900000000003);
	}

	public AdjustPM(double alphaIncTS, double betaIncTS, double alphaDecTS, double betaDecTS, double initPM,double alphaIncPM, double betaIncPM, double alphaDecPM, double betaDecPM) {
		_alphaIncTS = alphaIncTS;
		_betaIncTS = betaIncTS;
		_alphaDecTS = alphaDecTS;
		_betaDecTS = betaDecTS;
		_alphaIncPM = alphaIncPM;
		_betaIncPM = betaIncPM;
		_alphaDecPM = alphaDecPM;
		_betaDecPM = betaDecPM;
		_initPM = initPM;
	}

	@Override
	public void initBidder() {
		super.initBidder();

		_PM = new HashMap<Query, Double>();
		for (Query q : _querySpace) {
			_PM.put(q, _initPM);
		}

		_salesDistribution = new HashMap<Query, Double>();
		for (Query q : _querySpace) {
			_salesDistribution.put(q, 1.0/_querySpace.size());
		}
	}

	@Override
	public BidBundle getBidBundle(Set<AbstractModel> models) {
		buildMaps(models);
		_bidBundle = new BidBundle();

		if(_day < 2) { 
			_bidBundle = new BidBundle();
			for(Query q : _querySpace) {
				double bid = getRandomBid(q);
				_bidBundle.addQuery(q, bid, new Ad(), getDailySpendingLimit(q, bid));
			}
			return _bidBundle;
		}

		/*
		 * Calculate Average PM
		 */
		double avgPM = 0.0;
		double totWeight = 0;
		for(Query q : _querySpace) {
			if(_queryReport.getCost(q) != 0 &&
					_salesReport.getRevenue(q) !=0) {
				double weight = _salesDistribution.get(q);
				avgPM += ((_salesReport.getRevenue(q) - _queryReport.getCost(q))/_salesReport.getRevenue(q)) * weight;
				totWeight+=weight;
			}
		}
		avgPM /= totWeight;
		if(Double.isNaN(avgPM)) {
			avgPM = _initPM;
		}

		/*
		 * Adjust Target Sales
		 */
		double totDesiredSales = 0;
		for(Query q : _querySpace) {
			if(_queryReport.getCost(q) != 0 &&
					_salesReport.getRevenue(q) !=0) {
				if ((_salesReport.getRevenue(q) - _queryReport.getCost(q))/_salesReport.getRevenue(q) < avgPM) {
					_salesDistribution.put(q, _salesDistribution.get(q)*(1-(_alphaDecTS * Math.abs((_salesReport.getRevenue(q) - _queryReport.getCost(q))/_salesReport.getRevenue(q) - avgPM) +  _betaDecTS)));
				}
				else {
					_salesDistribution.put(q, _salesDistribution.get(q)*(1+_alphaIncTS * Math.abs((_salesReport.getRevenue(q) - _queryReport.getCost(q))/_salesReport.getRevenue(q) - avgPM)  +  _betaIncTS));
				}
			}
			totDesiredSales += _salesDistribution.get(q);
		}

		/*
		 * Normalize
		 */
		double normFactor = 1.0/totDesiredSales;
		for(Query q : _querySpace) {
			_salesDistribution.put(q, _salesDistribution.get(q)*normFactor);
		}

		/*
		 * Adjust PM
		 */
		if(_day > 1) {
			adjustPM();
		}

		for (Query query : _querySpace) {
			double targetCPC = getTargetCPC(query);
			_bidBundle.setBid(query, targetCPC+.01);

			if (TARGET) {
				if (query.getType().equals(QueryType.FOCUS_LEVEL_ZERO))
					_bidBundle.setAd(query, new Ad(new Product(_manSpecialty, _compSpecialty)));
				if (query.getType().equals(QueryType.FOCUS_LEVEL_ONE) && query.getComponent() == null)
					_bidBundle.setAd(query, new Ad(new Product(query.getManufacturer(), _compSpecialty)));
				if (query.getType().equals(QueryType.FOCUS_LEVEL_ONE) && query.getManufacturer() == null)
					_bidBundle.setAd(query, new Ad(new Product(_manSpecialty, query.getComponent())));
				if (query.getType().equals(QueryType.FOCUS_LEVEL_TWO) && query.getManufacturer().equals(_manSpecialty)) 
					_bidBundle.setAd(query, new Ad(new Product(_manSpecialty, query.getComponent())));
			}

			if(DAILYBUDGET) {
				_bidBundle.setDailyLimit(query, getDailySpendingLimit(query,targetCPC));
			}

		}
		if(BUDGET) {
			_bidBundle.setCampaignDailySpendLimit(getTotalSpendingLimit(_bidBundle));
		}
		return _bidBundle;
	}

	protected double getTargetCPC(Query q) {
		double conversion;
		if (_day <= 6)
			conversion = _baselineConversion.get(q);
		else
			conversion = _conversionPrModel.getPrediction(q);
		double CPC = _salesPrices.get(q)*(1 - _PM.get(q))*conversion;
		CPC = Math.max(0.0, Math.min(3.5, CPC));
		return CPC;
	}

	protected void adjustPM() {
		for(Query q : _querySpace) {
			double tmp = _PM.get(q);
			if (_salesReport.getConversions(q) >= _salesDistribution.get(q)*_dailyCapacity) {
				tmp *= (1+_alphaIncPM * Math.abs(_salesReport.getConversions(q) - _salesDistribution.get(q)*_dailyCapacity) +  _betaIncPM);
			} else {
				tmp *= (1-(_alphaDecPM * Math.abs(_salesReport.getConversions(q) - _salesDistribution.get(q)*_dailyCapacity) +  _betaDecPM));
			}
			if(Double.isNaN(tmp) || tmp <= 0) {
				tmp = _initPM;
			}
			if(tmp > 1.0) {
				tmp = 1.0;
			}
			_PM.put(q, tmp);
		}
	}

	@Override
	protected double getDailySpendingLimit(Query q, double targetCPC) {
		if(_day >= 6 && _conversionPrModel != null) {
			return (targetCPC * _salesDistribution.get(q)*_dailyCapacity) / _conversionPrModel.getPrediction(q);
		}
		else {
			return (targetCPC * _salesDistribution.get(q)*_dailyCapacity) / _baselineConversion.get(q);
		}
	}

	@Override
	public String toString() {
		return "AdjustPM";
	}

	@Override
	public AbstractAgent getCopy() {
		return new AdjustPM(_alphaIncTS,_betaIncTS,_alphaDecTS,_betaDecTS,_initPM, _alphaIncPM, _betaIncPM, _alphaDecPM, _betaDecPM);
	}
}