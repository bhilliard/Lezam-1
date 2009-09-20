package simulator.models;

import java.util.HashMap;
import java.util.LinkedList;

import newmodels.AbstractModel;
import newmodels.bidtoprclick.AbstractBidToPrClick;
import simulator.BasicSimulator;
import simulator.Reports;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.SalesReport;

public class PerfectBidToPrClick extends AbstractBidToPrClick {

	private HashMap<Query, HashMap<Double, Reports>> _allReportsMap;
	private HashMap<Query, double[]> _potentialBidsMap;

	public PerfectBidToPrClick(HashMap<Query, HashMap<Double, Reports>> allReportsMap, HashMap<Query, double[]> potentialBidsMap) {
		_allReportsMap = allReportsMap;
		_potentialBidsMap = potentialBidsMap;
	}

	@Override
	public double getPrediction(Query query, double bid, Ad currentAd) {
		if(bid == 0) {
			return 0.0;
		}
		double clickPr;
		HashMap<Double, Reports> queryReportMaps = _allReportsMap.get(query);
		Reports reports = queryReportMaps.get(bid);
		QueryReport queryReport;
		if(reports == null) {
			double closestBid = getClosestElement(_potentialBidsMap.get(query),bid);
			Reports closestReports = queryReportMaps.get(closestBid);
			queryReport = closestReports.getQueryReport();
		}
		else {
			queryReport = reports.getQueryReport();
		}
		int clicks = queryReport.getClicks(query);
		int impressions = queryReport.getImpressions(query);
		if(clicks == 0 || impressions == 0) {
			clickPr = 0.0;
		}
		else {
			clickPr = clicks/(impressions*1.0);
		}
		return clickPr;
	}

	private double getClosestElement(double[] arr, double bid) {
		double lastDiff = Double.MAX_VALUE;
		for(int i = 0; i < arr.length; i++) {
			double elem = arr[i];
			if(elem == bid) {
				return elem;
			}
			double diff = bid - elem;
			if(diff < lastDiff) { 
				lastDiff = diff;
			}
			else {
				return arr[i-1];
			}
		}
		return arr[arr.length-1];
	}

	@Override
	public boolean updateModel(QueryReport queryReport, SalesReport salesReport, BidBundle bundle) {
		return true;
	}

	@Override
	public AbstractModel getCopy() {
		return new PerfectBidToPrClick(_allReportsMap,_potentialBidsMap);
	}

	@Override
	public void setSpecialty(String manufacturer, String component) {
		//not needed
	}

	@Override
	public String toString() {
		return "PerfectBidToPrClick";
	}

}
