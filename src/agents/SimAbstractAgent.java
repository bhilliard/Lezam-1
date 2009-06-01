/**
 * Abstract agent class for agents that can be run in the simulator
 */
package agents;

import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import newmodels.AbstractModel;


import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.AdvertiserInfo;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Product;
import edu.umich.eecs.tac.props.PublisherInfo;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.QueryType;
import edu.umich.eecs.tac.props.RetailCatalog;
import edu.umich.eecs.tac.props.SalesReport;
import edu.umich.eecs.tac.props.SlotInfo;
import se.sics.isl.transport.Transportable;
import se.sics.tasim.aw.Agent;
import se.sics.tasim.aw.Message;
import se.sics.tasim.props.SimulationStatus;
import se.sics.tasim.props.StartInfo;

/**
 * @author jberg
 *
 */
public abstract class SimAbstractAgent extends Agent {

	
	
    /**
     * Basic simulation information. {@link StartInfo} contains
     * <ul>
     * <li>simulation ID</li>
     * <li>simulation start time</li>
     * <li>simulation length in simulation days</li>
     * <li>actual seconds per simulation day</li>
     * </ul>
     * An agent should receive the {@link StartInfo} at the beginning of the game or during recovery.
     */
    private StartInfo _startInfo;

    /**
     * Basic auction slot information. {@link SlotInfo} contains
     * <ul>
     * <li>the number of regular slots</li>
     * <li>the number of promoted slots</li>
     * <li>promoted slot bonus</li>
     * </ul>
     * An agent should receive the {@link SlotInfo} at the beginning of the game or during recovery.
     * This information is identical for all auctions over all query classes.
     */
    protected SlotInfo _slotInfo;

    /**
     * The retail catalog. {@link RetailCatalog} contains
     * <ul>
     * <li>the product set</li>
     * <li>the sales profit per product</li>
     * <li>the manufacturer set</li>
     * <li>the component set</li>
     * </ul>
     * An agent should receive the {@link RetailCatalog} at the beginning of the game or during recovery.
     */
    protected RetailCatalog _retailCatalog;

    /**
     * The basic advertiser specific information. {@link AdvertiserInfo} contains
     * <ul>
     * <li>the manufacturer specialty</li>
     * <li>the component specialty</li>
     * <li>the manufacturer bonus</li>
     * <li>the component bonus</li>
     * <li>the distribution capacity discounter</li>
     * <li>the address of the publisher agent</li>
     * <li>the distribution capacity</li>
     * <li>the address of the advertiser agent</li>
     * <li>the distribution window</li>
     * <li>the target effect</li>
     * <li>the focus effects</li>
     * </ul>
     * An agent should receive the {@link AdvertiserInfo} at the beginning of the game or during recovery.
     */
    protected AdvertiserInfo _advertiserInfo;

    /**
     * The basic publisher information. {@link PublisherInfo} contains
     * <ul>
     * <li>the squashing parameter</li>
     * </ul>
     * An agent should receive the {@link PublisherInfo} at the beginning of the game or during recovery.
     */
    protected PublisherInfo _publisherInfo;

    /**
     * The list contains all of the {@link SalesReport sales report} delivered to the agent.  Each
     * {@link SalesReport sales report} contains the conversions and sales revenue accrued by the agent for each query
     * class during the period.
     */
    protected Queue<SalesReport> _salesReports;

    /**
     * The list contains all of the {@link QueryReport query reports} delivered to the agent.  Each
     * {@link QueryReport query report} contains the impressions, clicks, cost, average position, and ad displayed
     * by the agent for each query class during the period as well as the positions and displayed ads of all advertisers
     * during the period for each query class.
     */
    protected Queue<QueryReport> _queryReports;

    /**
     * List of all the possible queries made available in the {@link RetailCatalog retail catalog}.
     */
    protected Set<Query> _querySpace;
    
    /**
     * Set of models that our agent uses
     */
    protected Set<AbstractModel> _models;

    /**
     * The current day, or -1 for pregame
     */
    protected double _day;
    
    /**
     * The squashing parameter
     */
	protected double _squashing;
	
	/**
	 * Most recent Query Report
	 */
	protected QueryReport _queryReport;
	
	/**
	 * Most recent Sales Report
	 */
	protected SalesReport _salesReport;
	
	/**
	 * Promoted Slot Bonus
	 */
	protected double _PSB;
	
	/**
	 * Number of Promoted Slots
	 */
	protected int _numPS;
	
	/**
	 * Number of Regular Slots
	 */
	protected int _numRS;
	
	/**
	 * Total number of Slots
	 */
	protected int _numSlots;
	
	/**
	 * Number of days in the simulation
	 */
	protected int _numDays;
	
	/**
	 * Our advertiser string
	 */
	protected String _advId;
	
	/**
	 * Component Specialty Bonus
	 */
	protected double _CSB;
	
	/**
	 * Component Specialty
	 */
	protected String _compSpecialty;

	/**
	 * Capacity limit
	 */
	protected int _capacity;

	/**
	 * Capacity Discounter
	 */
	protected double _lambda;

	/**
	 * Capacity window length
	 */
	protected int _capWindow;

	/**
	 * Baseline conversion rate for F0 users
	 */
	protected double _piF0;

	/**
	 * Baseline conversion rate for F1 users
	 */
	protected double _piF1;

	/**
	 * Baseline conversion rate for F2 users
	 */
	protected double _piF2;

	/**
	 * Manufacturer Specialty Bonus
	 */
	protected double _MSB;

	/**
	 * Manufacturer Specilaty
	 */
	protected String _manSpecialty;

	/**
	 * Targeted Ad Effect
	 */
	protected double _targEffect;

	
	
	/**
	 * 
	 */
	public SimAbstractAgent() {
		_salesReports = new LinkedList<SalesReport>();
		_queryReports = new LinkedList<QueryReport>();
		_querySpace = new LinkedHashSet<Query>();
		_day = -1;
	}

    /**
     * Processes the messages received the by agent from the server.
     *
     * @param message the message
     */
    protected void messageReceived(Message message) {
        Transportable content = message.getContent();

        if (content instanceof QueryReport) {
            handleQueryReport((QueryReport) content);
        } else if (content instanceof SalesReport) {
            handleSalesReport((SalesReport) content);
        } else if (content instanceof SimulationStatus) {
            handleSimulationStatus((SimulationStatus) content);
        } else if (content instanceof PublisherInfo) {
            handlePublisherInfo((PublisherInfo) content);
        } else if (content instanceof SlotInfo) {
            handleSlotInfo((SlotInfo) content);
        } else if (content instanceof RetailCatalog) {
            handleRetailCatalog((RetailCatalog) content);
        } else if (content instanceof AdvertiserInfo) {
            handleAdvertiserInfo((AdvertiserInfo) content);
        } else if (content instanceof StartInfo) {
            handleStartInfo((StartInfo) content);
        }
        else {
        	throw new RuntimeException("received unexpected message: "+content);
        }
    }

    /**
     * Sends a constructed {@link BidBundle} from any updated bids, ads, or spend limits.
     */
    protected void sendBidAndAds() {
    	updateModels(_salesReport, _queryReport, _models);
        BidBundle bidBundle = getBidBundle(_models);
        String publisherAddress = _advertiserInfo.getPublisherId();
        // Send the bid bundle to the publisher
        if (publisherAddress != null) {
            sendMessage(publisherAddress, bidBundle);
        }
    }


    /**
     * Processes an incoming query report.
     *
     * @param queryReport the daily query report.
     */
    protected void handleQueryReport(QueryReport queryReport) {
        _queryReports.add(queryReport);
        _queryReport = queryReport;
    }

    
    /**
     * Processes an incoming sales report.
     *
     * @param salesReport the daily sales report.
     */
    protected void handleSalesReport(SalesReport salesReport) {
        _salesReports.add(salesReport);
        _salesReport = salesReport;
    }

    /**
     * Processes a simulation status notification.  Each simulation day the {@link SimulationStatus simulation status }
     * notification is sent after the other daily messages ({@link QueryReport} {@link SalesReport} have been sent.
     *
     * @param simulationStatus the daily simulation status.
     */
    protected void handleSimulationStatus(SimulationStatus simulationStatus) {
    	_day = simulationStatus.getCurrentDate();
    	sendBidAndAds();
    }

    /**
     * Processes the publisher information.
     * @param publisherInfo the publisher information.
     */
    protected void handlePublisherInfo(PublisherInfo publisherInfo) {
    	_squashing = publisherInfo.getSquashingParameter();
        this._publisherInfo = publisherInfo;
    }

    /**
     * Processrs the slot information.
     * @param slotInfo the slot information.
     */
    protected void handleSlotInfo(SlotInfo slotInfo) {
    	_PSB = slotInfo.getPromotedSlotBonus();
    	_numPS = slotInfo.getPromotedSlots();
    	_numRS = slotInfo.getRegularSlots();
    	_numSlots = _numPS + _numRS;
        this._slotInfo = slotInfo;
    }

    /**
     * Processes the retail catalog.
     * @param retailCatalog the retail catalog.
     */
    protected void handleRetailCatalog(RetailCatalog retailCatalog) {
        this._retailCatalog = retailCatalog;
        // The query space is all the F0, F1, and F2 queries for each product
        // The F0 query class
        if(retailCatalog.size() > 0) {
            _querySpace.add(new Query(null, null));
        }

        for(Product product : retailCatalog) {
            // The F1 query classes
            // F1 Manufacturer only
            _querySpace.add(new Query(product.getManufacturer(), null));
            // F1 Component only
            _querySpace.add(new Query(null, product.getComponent()));

            // The F2 query class
            _querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
        }
    }

    /**
     * Processes the advertiser information.
     * @param advertiserInfo the advertiser information.
     */
    protected void handleAdvertiserInfo(AdvertiserInfo advertiserInfo) {
        this._advertiserInfo = advertiserInfo;
        _advId = advertiserInfo.getAdvertiserId();
        _CSB = advertiserInfo.getComponentBonus();
        _compSpecialty = advertiserInfo.getComponentSpecialty();
        _capacity = advertiserInfo.getDistributionCapacity();
        _lambda = advertiserInfo.getDistributionCapacityDiscounter();
        _capWindow = advertiserInfo.getDistributionWindow();
        _piF0 = advertiserInfo.getFocusEffects(QueryType.FOCUS_LEVEL_ZERO);
        _piF1 = advertiserInfo.getFocusEffects(QueryType.FOCUS_LEVEL_ONE);
        _piF2 = advertiserInfo.getFocusEffects(QueryType.FOCUS_LEVEL_TWO);
        _MSB = advertiserInfo.getManufacturerBonus();
        _manSpecialty = advertiserInfo.getManufacturerSpecialty();
        _targEffect = advertiserInfo.getTargetEffect();
    }

    /**
     * Processes the start information.
     * @param startInfo the start information.
     */
    protected void handleStartInfo(StartInfo startInfo) {
        this._startInfo = startInfo;
        _numDays = startInfo.getNumberOfDays();
    }

    /**
     * Prepares the agent for a new simulation.
     */
    protected void simulationSetup() {
    	_models = initModels();
    	initBidder();
    }

    /**
     * Runs any post-processes required for the agent after a simulation ends.
     */
    protected void simulationFinished() {
        _salesReports.clear();
        _queryReports.clear();
        _querySpace.clear();
        _day = 0;
    }
    
    
    /*
     * This method will be run once at the beginning of each simulation to initialize the
     * models that our agent will use
     */
    protected abstract Set<AbstractModel> initModels();
    
    /*
     * This will be called once each day before getBidBundle to update all the models
     * that the agent needs to make a bid bundle
     */
    protected abstract void updateModels(SalesReport salesReport,
    													QueryReport queryReport,
    													Set<AbstractModel> models);
    
    /*
     * This method will be run once at the beginning of each simulation to initialize the
     * bidding strategy that our agent will use
     */
    protected abstract void initBidder();
    
    /*
     * This will be called once each day to get the bid bundle for the day, i.e. the bids,
     * budgets, and ad types
     */
    protected abstract BidBundle getBidBundle(Set<AbstractModel> models);


}