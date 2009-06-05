package agents;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import com.sun.xml.internal.ws.api.pipe.NextAction;

import agents.rules.ConversionPr;
import agents.rules.DistributionCap;
import agents.rules.ManufacurerBonus;
import agents.rules.NoImpressions;
import agents.rules.ReinvestmentCap;
import agents.rules.Targeted;
import agents.rules.TopPosition;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Product;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.QueryType;
import edu.umich.eecs.tac.props.SalesReport;

public class PosXAgent extends AbstractAgent{

	protected PosXBidStrategy _bidStrategy;
	protected DistributionCap _distributionCap;
	protected ReinvestmentCap _reinvestmentCap;
	protected TopPosition _topPosition;
	protected NoImpressions _noImpressions;
	//protected Hashtable <Query , []> _CPC;

	protected double _campaignSpendLimit;

	protected int _myPosX = 4;
	protected int _day;

	public PosXAgent(){
		_day = 0;
		//_CPC = new Hashtable<Query, Integer[]>();
	}

	@Override
	protected void simulationSetup() {}

	@Override
	protected void initBidder() {
		printAdvertiserInfo();

		_bidStrategy = new PosXBidStrategy(_querySpace, _myPosX);
		_bidStrategy.setDistributionCapacity(_advertiserInfo.getDistributionCapacity(),_advertiserInfo.getDistributionWindow());
		_bidStrategy.setPP(getAvaregeProductPrice());

	}


	@Override
	protected void updateBidStrategy() {
		_day++;
		if (_day < 3) return;
		QueryReport qr = _queryReports.remove();
		SalesReport sr = _salesReports.remove();
		
		if (qr == null || sr == null) {
			return;
		}
		for(Query q : _querySpace) {
			//_CPC.put (q, )
			_bidStrategy.setData(q, qr.getCPC(q), (int)qr.getPosition(q));	
			System.out.println ("ubs_pos: " + qr.getPosition(q) + " " + (int)qr.getPosition(q));
			System.out.println ("ubs_CPC: " + qr.getCPC(q));
		}

	}


	@Override
	protected BidBundle buildBidBudle(){
		System.out.println("**********");
		System.out.println(_bidStrategy);
		System.out.println("**********");
		return _bidStrategy.buildBidBundle();
	}

	private void printit(HashMap<String, Double> p) {
		for (Map.Entry<String, Double> s :  p.entrySet()) {
			System.out.print(s.getKey() + ": " + s.getValue() + " || ");
		}
		System.out.println();
	}



}