package agents;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

import newmodels.AbstractModel;
import newmodels.bidtocpc.AbstractBidToCPC;
import newmodels.bidtocpc.RegressionBidToCPC;
import newmodels.bidtoslot.BasicBidToClick;
import newmodels.prconv.AbstractPrConversionModel;
import newmodels.prconv.GoodConversionPrModel;
import newmodels.prconv.NewAbstractConversionModel;
import newmodels.unitssold.AbstractUnitsSoldModel;
import newmodels.unitssold.UnitsSoldMovingAvg;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Product;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.QueryType;
import edu.umich.eecs.tac.props.SalesReport;

public class H3 extends SimAbstractAgent{
	protected AbstractUnitsSoldModel _unitsSoldModel;
	protected NewAbstractConversionModel _conversionPrModel;
    protected HashMap<Query, Double> _baselineConv;
	protected HashMap<Query,Double> _estimatedPrice;
	protected AbstractBidToCPC _bidToCPC;
	protected HashMap<Query, BasicBidToClick> _bidToclick;
	protected double oldSales = 0.0;
	protected double newSales = 0.0;
	//k is a constant that equates EPPS across queries
	protected double k;
	protected BidBundle _bidBundle;
	protected ArrayList<BidBundle> _bidBundles;

	protected int _timeHorizon;
	protected final int MAX_TIME_HORIZON = 5;
	protected final double MAX_BID_CPC_GAP = 1.5;
	protected PrintStream output;	
	
	
	@Override
	public BidBundle getBidBundle(Set<AbstractModel> models) {
		for(Query query: _querySpace){
			_bidToclick.get(query).updateModel(_salesReport, _queryReport);
		}

		if (_day > 1 && _salesReport != null && _queryReport != null) {
			updateK();
		}
		
		for(Query query: _querySpace){
	
			_bidBundle.setBid(query, getQueryBid(query));
		
			//_bidBundle.setDailyLimit(query, setQuerySpendLimit(query));
		}
		_bidBundles.add(_bidBundle);
		return _bidBundle;
	}

	@Override
	public void initBidder() {	
			_bidBundle = new BidBundle();
		    for (Query query : _querySpace) {	
				_bidBundle.setBid(query, getQueryBid(query));
			}
		    
		    _bidBundles = new ArrayList<BidBundle>();
		    initializeK();

			try {
				output = new PrintStream(new File("h3.txt"));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
	}

	@Override
	public Set<AbstractModel> initModels() {
		_unitsSoldModel = new UnitsSoldMovingAvg(_querySpace, _capacity, _capWindow);

		_conversionPrModel = new GoodConversionPrModel(_querySpace);

		_estimatedPrice = new HashMap<Query, Double>();
		

		_bidToclick = new HashMap<Query, BasicBidToClick>();
		for (Query query : _querySpace) {
			_bidToclick.put(query, new BasicBidToClick(query, false));	
		}
		
		for(Query query:_querySpace){
			if(query.getType()== QueryType.FOCUS_LEVEL_ZERO){
				_estimatedPrice.put(query, 10.0 + 5/3);
			}
			if(query.getType()== QueryType.FOCUS_LEVEL_ONE){
				if(_manSpecialty.equals(query.getManufacturer())) _estimatedPrice.put(query, 15.0);
				else{
					if(query.getManufacturer() != null) _estimatedPrice.put(query, 10.0);
					else _estimatedPrice.put(query, 10.0 + 5/3);
				}
			}
			if(query.getType()== QueryType.FOCUS_LEVEL_TWO){
				if(_manSpecialty.equals(query.getManufacturer())) _estimatedPrice.put(query, 15.0);
				else _estimatedPrice.put(query, 10.0);
			}
		}

		_bidToCPC = new RegressionBidToCPC(_querySpace);
		
		_baselineConv = new HashMap<Query, Double>();
        for(Query q: _querySpace){
        	if(q.getType() == QueryType.FOCUS_LEVEL_ZERO) _baselineConv.put(q, 0.1);
        	if(q.getType() == QueryType.FOCUS_LEVEL_ONE){
        		if(q.getComponent() == _compSpecialty) _baselineConv.put(q, 0.27);
        		else _baselineConv.put(q, 0.2);
        	}
        	if(q.getType()== QueryType.FOCUS_LEVEL_TWO){
        		if(q.getComponent()== _compSpecialty) _baselineConv.put(q, 0.39);
        		else _baselineConv.put(q,0.3);
        	}
        }
		return null;
	}

	@Override
	public void updateModels(SalesReport salesReport, QueryReport queryReport) {
		// update models
		if (_day > 1 && _salesReport != null && _queryReport != null) {
			
			for(Query query: _querySpace){
				_bidToclick.get(query).updateModel(_salesReport, _queryReport);
			}
	
			_unitsSoldModel.update(_salesReport);
		    
			   _timeHorizon = (int)Math.min(Math.max(1,_day - 1), MAX_TIME_HORIZON);

               _conversionPrModel.setTimeHorizon(_timeHorizon);
               _conversionPrModel.updateModel(queryReport, salesReport);

			if (_bidBundles.size() > 1) 
				_bidToCPC.updateModel(_queryReport, _bidBundles.get(_bidBundles.size() - 2));

		}

	}


   protected double updateK(){
	  oldSales = newSales;
	  newSales = 0.0;
	  for(Query query: _querySpace){
		  newSales += _salesReport.getConversions(query);
	  }
	  double dailyLimit = (2*_capacity/5) - newSales;
	  double error = 0.5;
	  int counter = 0;
	  //initial guess of k is 5, and k never goes over 10
	  k = 10;
	  double sum = 0.0;
	  double hi =  14.5;
	  double lo = 1;
	  boolean done = false;
	  while(done == false && counter <= 20){
		  for (Query query: _querySpace){
			  sum += calcUnitSold(query, k);
		  }
		  if(sum < dailyLimit && dailyLimit - sum > error) {
			  hi =  k;
			  k = (lo + k)/2;
			  
		  }
		  else{
			  if(sum > dailyLimit && sum - dailyLimit > error) {
				  lo = k;
				  k = (hi + k)/2;
				  
			  }
			  else{ 
				  done = true;
				 
			  
			  } 
		  }
		  counter ++;
		  sum = 0.0;
	  }
	  
	  if(k > 14.5) k = 14.5;
	  if(k < 1) k = 1;
	  return k;
   }
   
   protected double calcUnitSold(Query q, double k){
	  double conversion = _conversionPrModel.getPrediction(q);
	  double cpc = (_estimatedPrice.get(q)-k)*conversion;
	  //use the bid to click model to estimate #clicks
	  double clicks = _bidToclick.get(q).getPrediction(cpc);
	  //estimated sales = clicks * conv prob 
	  return Math.max(0,clicks*conversion);
   }

	protected double cpcTobid(double cpc, Query query){
		if (_day <= 6) return cpc + .1;
		double bid = cpc + .1;
		while (_bidToCPC.getPrediction(query,bid) < cpc){
			bid += 0.1;
			if (bid - cpc >= MAX_BID_CPC_GAP) break;
		}     
		return bid;
	}
   
   protected void initializeK(){
	   if (_capacity == 500) k = 12;
	   else if (_capacity == 400) k = 11;
	   else k = 10;
   }
 
   protected double getQueryBid(Query q){
		double prConv;
		if(_day <= 3) {
			prConv = _baselineConv.get(q);
		}
		else if(_day <= 6){
			prConv = 0.9*_baselineConv.get(q);
		}
		else prConv = _conversionPrModel.getPrediction(q);
		
		double bid;
		bid = cpcTobid((_estimatedPrice.get(q) - k)*prConv,q);
		if(bid <= 0) return 0;
		else{
			if(bid > 2.5) return 2.5;
			else return bid;
		}

   }
  
   protected double setQuerySpendLimit(Query q){
	   return 0;
	}
   
	protected void printInfo() {
		// print debug info
		StringBuffer buff = new StringBuffer(255);
		buff.append("****************\n");
		for(Query q : _querySpace){
			buff.append("\t").append("Day: ").append(_day).append("\n");
			buff.append(q).append("\n");
			buff.append("\t").append("k: ").append(k).append("\n");
			buff.append("\t").append("Bid: ").append(_bidBundle.getBid(q)).append("\n");
			buff.append("\t").append("Window Sold: ").append(_unitsSoldModel.getWindowSold()).append("\n");
			buff.append("\t").append("capacity: ").append(_capacity).append("\n");
			buff.append("\t").append("Yesterday Sold: ").append(_unitsSoldModel.getLatestSample()).append("\n");
			/*if (_salesReport.getConversions(q) > 0) 
				buff.append("\t").append("Revenue: ").append(_salesReport.getRevenue(q)/_salesReport.getConversions(q)).append("\n");
			else buff.append("\t").append("Revenue: ").append("0.0").append("\n");*/
			if (_queryReport.getClicks(q) > 0) 
				buff.append("\t").append("Conversion Pr: ").append(_salesReport.getConversions(q)*1.0/_queryReport.getClicks(q)).append("\n");
			else buff.append("\t").append("Conversion Pr: ").append("No Clicks").append("\n");
			//buff.append("\t").append("Predicted Conversion Pr:").append(_prConversionModels.get(q).getPrediction()).append("\n");
			buff.append("\t").append("Conversions: ").append(_salesReport.getConversions(q)).append("\n");
			if (_salesReport.getConversions(q) > 0)
				buff.append("\t").append("Profit: ").append((_salesReport.getRevenue(q) - _queryReport.getCost(q))/(_queryReport.getClicks(q))).append("\n");
			else buff.append("\t").append("Profit: ").append("0").append("\n");
			buff.append("\t").append("Average Position:").append(_queryReport.getPosition(q)).append("\n");
			buff.append("****************\n");
		}
		
		System.out.println(buff);
		output.append(buff);
		output.flush();
	
	}

}

