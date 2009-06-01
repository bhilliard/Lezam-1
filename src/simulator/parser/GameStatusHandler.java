package simulator.parser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import se.sics.isl.transport.Transportable;
import se.sics.isl.util.IllegalConfigurationException;
import se.sics.tasim.logtool.LogReader;
import se.sics.tasim.logtool.ParticipantInfo;
import se.sics.tasim.props.SimulationStatus;
import edu.umich.eecs.tac.props.AdvertiserInfo;
import edu.umich.eecs.tac.props.BankStatus;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.PublisherInfo;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.ReserveInfo;
import edu.umich.eecs.tac.props.RetailCatalog;
import edu.umich.eecs.tac.props.SalesReport;
import edu.umich.eecs.tac.props.SlotInfo;
import edu.umich.eecs.tac.props.UserClickModel;

/**
*
* @author jberg
* 
*/
public class GameStatusHandler {

	
	private GameStatus _gameStatus;


	public GameStatusHandler(String filename) throws IOException, ParseException {
		_gameStatus = parseGameLog(filename);
	}

	public GameStatus parseGameLog(String filename) throws IOException, ParseException {
	    InputStream inputStream = new FileInputStream(filename);
    	GameLogParser parser = new GameLogParser(new LogReader(inputStream));
        parser.start();
        parser.stop();
	    LinkedList<SimParserMessage> messages = parser.getMessages();

	    ParticipantInfo[] participants = parser.getParticipants();
	    String[] participantNames = parser.getParticipantNames();
	    boolean[] isAdvertiser = parser.getIsAdvertiser();
	    int numadvertisers = 0;
	    ArrayList<String> advertiserslist = new ArrayList<String>();
	    for(int i = 0; i < participants.length; i++) { 
	    	if(isAdvertiser[i]) {
	    		numadvertisers++;
	    		advertiserslist.add(participantNames[i]);
	    	}
	    }
	    String[] advertisers = new String[numadvertisers];
	    advertisers = advertiserslist.toArray(advertisers);
	
	    /*
	     * These variables will be used save the gamestate 
	     */
	    HashMap<String,LinkedList<BankStatus>> bankStatuses = new HashMap<String,LinkedList<BankStatus>>();
	    HashMap<String,LinkedList<BidBundle>> bidBundles = new HashMap<String, LinkedList<BidBundle>>();
	    HashMap<String,LinkedList<QueryReport>> queryReports = new HashMap<String, LinkedList<QueryReport>>();
	    HashMap<String,LinkedList<SalesReport>> salesReports = new HashMap<String, LinkedList<SalesReport>>();
	    HashMap<String,AdvertiserInfo> advertiserInfos = new HashMap<String, AdvertiserInfo>();
	    
	    SlotInfo slotInfo = null;
	    ReserveInfo reserveInfo = null;
	    PublisherInfo pubInfo = null;
	    AdvertiserInfo advInfo = null;
	    RetailCatalog retailCatalog = null;
	    UserClickModel userClickModel = null;
	    
	    for(int i = 0; i < advertisers.length; i++) {
	    	LinkedList<BankStatus> bankStatuslist = new LinkedList<BankStatus>();
	    	LinkedList<BidBundle> bidBundlelist = new LinkedList<BidBundle>();
	    	LinkedList<QueryReport> queryReportlist = new LinkedList<QueryReport>();
	    	LinkedList<SalesReport> salesReportlist = new LinkedList<SalesReport>();
	    	bankStatuses.put(advertisers[i], bankStatuslist);
	    	bidBundles.put(advertisers[i], bidBundlelist);
	    	queryReports.put(advertisers[i], queryReportlist);
	    	salesReports.put(advertisers[i], salesReportlist);
	    }
	    
	    boolean slotinfoflag = false;
	    boolean reserveinfoflag = false;
	    boolean pubinfoflag = false;
	    boolean retailcatalogflag = false;
	    boolean userclickmodelflag = false;
	    
	    int day = -1;
	    for(int i = 0; i < messages.size(); i++) {
	    	SimParserMessage mes = messages.get(i);
	    	int from = mes.getSender();
	    	int to = mes.getReceiver();
	    	Transportable content = mes.getContent();
	    	if (content instanceof BankStatus) {
	    		BankStatus bankstatustemp = (BankStatus) content;
	    		/*
	    		 * The first three bankstatus messages we get are all zeros.
	    		 * The first one is sent pregame, and the next two are sent on
	    		 * days one and two before we actually get bank information back
	    		 * because of the two day lag.
	    		 */
	    		if(day >= 2) {
	    			String name = participantNames[to];
	    			LinkedList<BankStatus> bankstatuslist = bankStatuses.get(name);
	    			bankstatuslist.addLast(bankstatustemp);
	    			bankStatuses.put(name, bankstatuslist);
	    		}
	    	}
	    	else if (content instanceof SimulationStatus) {
	    		SimulationStatus simstatustemp = (SimulationStatus) content;
	    		day = simstatustemp.getCurrentDate();
	    	}
	    	else if (content instanceof SlotInfo && !slotinfoflag) {
	    		SlotInfo slotinfotemp = (SlotInfo) content;
	    		slotInfo = slotinfotemp;
	    		slotinfoflag = true;
	    	}
	    	else if (content instanceof ReserveInfo  && !reserveinfoflag) {
	    		ReserveInfo reserveinfotemp = (ReserveInfo) content;
	    		reserveInfo = reserveinfotemp;
	    		reserveinfoflag = true;
	    		
	    	}
	    	else if (content instanceof PublisherInfo && !pubinfoflag) {
	    		PublisherInfo publisherinfotemp = (PublisherInfo) content;
	    		pubInfo = publisherinfotemp;
	    		pubinfoflag = true;
	    	}
	    	else if (content instanceof SalesReport) {
	    		SalesReport salesreporttemp = (SalesReport) content;
	    		/*
	    		 * The first three salesreports messages we get are all zeros.
	    		 * The first one is sent pregame, and the next two are sent on
	    		 * days one and two before we actually get bank information back
	    		 * because of the two day lag.
	    		 */
	    		if(day >= 2) {
	    			String name = participantNames[to];
	    			LinkedList<SalesReport> salesreportlist = salesReports.get(name);
	    			salesreportlist.addLast(salesreporttemp);
	    			salesReports.put(name, salesreportlist);
	    		}
	    	}
	    	else if (content instanceof QueryReport) {
	    		QueryReport queryreporttemp = (QueryReport) content;
	    		/*
	    		 * The first three queryreports messages we get are all zeros.
	    		 * The first one is sent pregame, and the next two are sent on
	    		 * days one and two before we actually get bank information back
	    		 * because of the two day lag.
	    		 */
	    		if(day >= 2) {
	    			String name = participantNames[to];
	    			LinkedList<QueryReport> queryreportlist = queryReports.get(name);
	    			queryreportlist.addLast(queryreporttemp);
	    			queryReports.put(name, queryreportlist);
	    		}
	    	}
	    	else if (content instanceof RetailCatalog && !retailcatalogflag) {
	    		RetailCatalog retailcatalogtemp = (RetailCatalog) content;
	    		retailCatalog = retailcatalogtemp;
	    		reserveinfoflag = true;
	    	}
	    	else if (content instanceof BidBundle) {
	    		BidBundle bidbundletemp = (BidBundle) content;
	    		/*
	    		 * The first three bidbundles messages we get are all zeros.
	    		 * The first one is sent pregame, and the next two are sent on
	    		 * days one and two before we actually get bank information back
	    		 * because of the two day lag.
	    		 */
	    		if(day >= 2) {
	    			String name = participantNames[from];
	    			LinkedList<BidBundle> bidbundlelist = bidBundles.get(name);
	    			bidbundlelist.addLast(bidbundletemp);
	    			bidBundles.put(name, bidbundlelist);
	    		}
	    	}
	    	else if (content instanceof UserClickModel && !userclickmodelflag) {
	    		UserClickModel userclickmodeltemp = (UserClickModel) content;
	    		userClickModel = userclickmodeltemp;
	    		userclickmodelflag = true;
	    	}
	    	else if (content instanceof AdvertiserInfo) {
	    		AdvertiserInfo advertiserinfotemp = (AdvertiserInfo) content;
	    		advInfo = advertiserinfotemp;
	    		String name = participantNames[to];
	    		advertiserInfos.put(name, advInfo);

	    	}
	    	else {
//	    		throw new RuntimeException("Unexpected parse token");
	    	}
	    }
	    
	    GameStatus gameStatus = new GameStatus(advertisers, bankStatuses, bidBundles, queryReports, salesReports, advertiserInfos,
	    		slotInfo, reserveInfo, pubInfo, advInfo, retailCatalog, userClickModel);
	    return gameStatus;
	}


	public GameStatus getGameStatus() {
		return _gameStatus;
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, IllegalConfigurationException, ParseException {
		String filename = "/pro/aa/usr/jberg/server/ver0.9.5/logs/sims/localhost_sim3.slg";
		GameStatusHandler gameStatusHandler = new GameStatusHandler(filename);
		GameStatus gameStatus = gameStatusHandler.getGameStatus();
		String[] advertisers = gameStatus.getAdvertisers();
		HashMap<String, LinkedList<BankStatus>> bankStatuses = gameStatus.getBankStatuses();
		HashMap<String, LinkedList<BidBundle>> bidBundles = gameStatus.getBidBundles();
		HashMap<String, LinkedList<QueryReport>> queryReports = gameStatus.getQueryReports();
		HashMap<String, LinkedList<SalesReport>> salesReports = gameStatus.getSalesReports();
		HashMap<String, AdvertiserInfo> advertiserInfos = gameStatus.getAdvertiserInfos();
		SlotInfo slotInfo = gameStatus.getSlotInfo();
		ReserveInfo reserveInfo = gameStatus.getReserveInfo();
		PublisherInfo pubInfo = gameStatus.getPubInfo();
		AdvertiserInfo advInfo = gameStatus.getAdvInfo();
		RetailCatalog retailCatalog = gameStatus.getRetailCatalog();
		UserClickModel userClickModel = gameStatus.getUserClickModel();
		for(int i = 0; i < advertisers.length; i++) {
			LinkedList<BankStatus> bankStatusList = bankStatuses.get(advertisers[i]);
			LinkedList<BidBundle> bidBundlesList = bidBundles.get(advertisers[i]);
			LinkedList<QueryReport> queryReportsList = queryReports.get(advertisers[i]);
			LinkedList<SalesReport> salesReportsList = salesReports.get(advertisers[i]);
			System.out.println(advertisers[i]);
			System.out.println("\t # of Bank Statuses: " + bankStatusList.size());
			for(int j = 0; j < bankStatusList.size(); j++) {
				System.out.println("\t\t " + bankStatusList.get(j));
			}
			System.out.println("\t # of Bid Bundles: " + bidBundlesList.size());
			for(int j = 0; j < bidBundlesList.size(); j++) {
				System.out.println("\t\t " + bidBundlesList.get(j));
			}
			System.out.println("\t # of Query Reports: " + queryReportsList.size());
			System.out.println("\t # of Sales Reports: " + salesReportsList.size());
		}
		
		/*
		 * TODO
		 * 
		 * Check that parsed log actually matches the game.....
		 */
	}

}