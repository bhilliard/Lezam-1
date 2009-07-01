package simulator.models;

import java.util.LinkedList;

import newmodels.bidtoprclick.AbstractBidToPrClick;
import simulator.BasicSimulator;
import simulator.Reports;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.SalesReport;

public class PerfectBidToPrClick extends AbstractBidToPrClick {

	private BasicSimulator _simulator;

	@Override
	public double getPrediction(Query query, double currentBid, Ad currentAd, BidBundle bidbundle){
		LinkedList<Reports> reports = _simulator.getSingleQueryReport(query, currentBid);
		double avgPrClick = 0;
		for(Reports report : reports) {
			if(report.getQueryReport().getImpressions(query) != 0) {
				avgPrClick += report.getQueryReport().getClicks(query)/((double)report.getQueryReport().getImpressions(query));
			}
		}
		avgPrClick = avgPrClick/((double) reports.size());
		return avgPrClick;
	}

	@Override
	public boolean updateModel(QueryReport queryReport, BidBundle bundle) {
		return true;
	}

}
