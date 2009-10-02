package simulator.predictions;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import newmodels.AbstractModel;
import newmodels.avgpostoposdist.AvgPosToPosDist;
import newmodels.bidtocpc.AbstractBidToCPC;
import newmodels.bidtocpc.ConstantBidToCPC;
import newmodels.bidtocpc.EnsembleBidToCPC;
import newmodels.bidtocpc.RegressionBidToCPC;
import newmodels.bidtopos.AbstractBidToPos;
import newmodels.bidtopos.RegressionBidToPos;
import newmodels.bidtopos.SpatialBidToPos;
import newmodels.bidtopos.EnsembleBidToPos;
import newmodels.bidtoprclick.AbstractBidToPrClick;
import newmodels.bidtoprclick.EnsembleBidToPrClick;
import newmodels.bidtoprclick.RegressionBidToPrClick;
import newmodels.cpctobid.AbstractCPCToBid;
import newmodels.cpctobid.BidToCPCInverter;
import newmodels.postobid.AbstractPosToBid;
import newmodels.postobid.BidToPosInverter;
import newmodels.postocpc.AbstractPosToCPC;
import newmodels.postocpc.EnsemblePosToCPC;
import newmodels.postocpc.RegressionPosToCPC;
import newmodels.postoprclick.AbstractPosToPrClick;
import newmodels.postoprclick.BasicPosToPrClick;
import newmodels.postoprclick.EnsemblePosToPrClick;
import newmodels.postoprclick.RegressionPosToPrClick;
import newmodels.targeting.BasicTargetModel;


import simulator.parser.GameStatus;
import simulator.parser.GameStatusHandler;
import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.AdvertiserInfo;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Product;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.SalesReport;

public class PredictionEvaluator {

	private boolean _ignoreNan = false;
	private double _outOfAuction = 6.0;

	public ArrayList<String> getGameStrings() {
		//		String baseFile = "/Users/jordanberg/Desktop/mckpgames/localhost_sim";
		//		String baseFile = "/home/jberg/mckpgames/localhost_sim";
		//		int min = 454;
		//		int max = 455;
		//		int max = 496;

		String baseFile = "/Users/jordanberg/Desktop/finalsgames/server1/game";
		int min = 1430;
		int max = 1431;
		//				int max = 9;

		//				String baseFile = "/pro/aa/finals/day-2/server-1/game";
		//				int min = 1430;
		//				int max = 1435;
		//		int max = 1464;

		ArrayList<String> filenames = new ArrayList<String>();
		//		System.out.println("Min: " + min + "  Max: " + max);
		for(int i = min; i < max; i++) { 
			filenames.add(baseFile + i + ".slg");
		}
		return filenames;
	}

	public void bidToPosToClickPrPredictionChallenge(AbstractBidToPos bidToPosBaseModel, AbstractPosToPrClick baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractPosToPrClick model = (AbstractPosToPrClick) baseModel.getCopy();
				AbstractBidToPos bidToPosModel = (AbstractBidToPos) bidToPosBaseModel.getCopy();
				model.setSpecialty(advInfo.getManufacturerSpecialty(),advInfo.getComponentSpecialty());

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					model.updateModel(queryReport, salesReport, bidBundle);
					if(i >= 6) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double bid = otherBidBundle.getBid(q);
							if(bid != 0) {
								double pos = bidToPosModel.getPrediction(q, bid);
								double error = model.getPrediction(q, pos, otherBidBundle.getAd(q));
								if(Double.isNaN(error)) {
									error = 0.0;
								}
								double clicks = otherQueryReport.getClicks(q);
								double imps = otherQueryReport.getImpressions(q);
								double clickPr = 0;
								if(!(clicks == 0 || imps == 0)) {
									clickPr = clicks/imps;
								}
								else {
									if(_ignoreNan ) {
										continue;
									}
								}
								error -= clickPr;
								error = error*error;
								ourTotActual += clickPr;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		//		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	public void posToClickPrPredictionChallenge(AbstractPosToPrClick baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractPosToPrClick model = (AbstractPosToPrClick) baseModel.getCopy();
				model.setSpecialty(advInfo.getManufacturerSpecialty(),advInfo.getComponentSpecialty());

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					model.updateModel(queryReport, salesReport, bidBundle);
					if(i >= 5) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double bid = otherBidBundle.getBid(q);
							if(bid != 0) {
								double error = model.getPrediction(q, otherQueryReport.getPosition(q), otherBidBundle.getAd(q));
								if(Double.isNaN(error)) {
									error = 0.0;
								}
								double clicks = otherQueryReport.getClicks(q);
								double imps = otherQueryReport.getImpressions(q);
								double clickPr = 0;
								if(!(clicks == 0 || imps == 0)) {
									clickPr = clicks/imps;
								}
								else {
									if(_ignoreNan ) {
										continue;
									}
								}
								error -= clickPr;
								error = error*error;
								ourTotActual += clickPr;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		//		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	public void bidToClickPrPredictionChallenge(AbstractBidToPrClick baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractBidToPrClick model = (AbstractBidToPrClick) baseModel.getCopy();
				model.setSpecialty(advInfo.getManufacturerSpecialty(),advInfo.getComponentSpecialty());

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					model.updateModel(queryReport, salesReport, bidBundle);

					if(i >= 5) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double bid = otherBidBundle.getBid(q);
							if(bid != 0) {
								double error = model.getPrediction(q, otherBidBundle.getBid(q), otherBidBundle.getAd(q));
								if(Double.isNaN(error)) {
									error = 0.0;
								}
								double clicks = otherQueryReport.getClicks(q);
								double imps = otherQueryReport.getImpressions(q);
								double clickPr = 0;
								if(!(clicks == 0 || imps == 0)) {
									clickPr = clicks/imps;
								}
								else {
									if(_ignoreNan ) {
										continue;
									}
								}
								error -= clickPr;
								error = error*error;
								ourTotActual += clickPr;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		//		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	public void bidToCPCPredictionChallenge(AbstractBidToCPC baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractBidToCPC model = (AbstractBidToCPC) baseModel.getCopy();

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					model.updateModel(queryReport, salesReport, bidBundle);

					if(i >= 5) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double bid = otherBidBundle.getBid(q);
							if(bid != 0) {
								double error = model.getPrediction(q, otherBidBundle.getBid(q));
								if(Double.isNaN(error)) {
									error = bid;
									continue;
								}
								double CPC = otherQueryReport.getCPC(q);
								if(Double.isNaN(CPC)) {
									if(_ignoreNan ) {
										continue;
									}
									CPC = 0.0;
								}
								error -= CPC;
								error = error*error;
								ourTotActual += CPC;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		//		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	public void posToCPCToBidPredictionChallenge(AbstractBidToCPC CPCtoBidBaseModel, AbstractPosToCPC baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractBidToCPC bidToCPCModel = (AbstractBidToCPC) CPCtoBidBaseModel.getCopy();
				AbstractPosToCPC posToCPCModel = (AbstractPosToCPC) baseModel.getCopy();
				AbstractCPCToBid CPCToBid = null;
				//TODO
				try {
					CPCToBid = new BidToCPCInverter(new RConnection(), querySpace, bidToCPCModel, .05, 0, 3.0);
				} catch (RserveException e) {
					e.printStackTrace();
				}

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					bidToCPCModel.updateModel(queryReport, salesReport, bidBundle);
					posToCPCModel.updateModel(queryReport, salesReport, bidBundle);
					CPCToBid.updateModel(queryReport, salesReport, bidBundle);

					if(i >= 6) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double pos = otherQueryReport.getPosition(q);
							double CPC = posToCPCModel.getPrediction(q, pos);
							double error = CPCToBid.getPrediction(q, CPC);
							if(Double.isNaN(error)) {
								error = 0.0;
							}
							double bid = otherBidBundle.getBid(q);
							//							System.out.println("Pos: " + pos + "  CPC: " + CPC + "  BidEst: " + error + "   bid: " + bid);
							if(bid != 0) {
								error -= bid;
								error = error*error;
								ourTotActual += bid;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		int dataPointCounter = 0;
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				dataPointCounter += totErrorCounter;
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}


	public void posToBidToCPCPredictionChallenge(AbstractBidToPos bidToPosBaseModel, AbstractBidToCPC baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractBidToPos bidToPosModel = (AbstractBidToPos) bidToPosBaseModel.getCopy();
				AbstractBidToCPC model = (AbstractBidToCPC) baseModel.getCopy();
				AbstractPosToBid posToBid = null;
				try {
					posToBid = new BidToPosInverter(new RConnection(), querySpace, bidToPosModel, .05, 0, 3.0);
				} catch (RserveException e) {
					e.printStackTrace();
				}

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					bidToPosModel.updateModel(queryReport, salesReport, bidBundle);
					model.updateModel(queryReport, salesReport, bidBundle);
					posToBid.updateModel(queryReport, salesReport, bidBundle);

					if(i >= 6) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double pos = otherQueryReport.getPosition(q);
							double bid = posToBid.getPrediction(q, pos);
							if(Double.isNaN(bid)) {
								bid = 0;
							}
							if(bid != 0) {
								double error = model.getPrediction(q, bid);
								if(Double.isNaN(error)) {
									error = bid;
									continue;
								}
								double CPC = otherQueryReport.getCPC(q);
								if(Double.isNaN(CPC)) {
									if(_ignoreNan ) {
										continue;
									}
									CPC = 0.0;
								}
								error -= CPC;
								error = error*error;
								ourTotActual += CPC;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		//		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	public void posToCPCPredictionChallenge(AbstractPosToCPC baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractPosToCPC model = (AbstractPosToCPC) baseModel.getCopy();

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					model.updateModel(queryReport, salesReport, bidBundle);
					if(i > 5) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double pos = otherQueryReport.getPosition(q);
							if(Double.isNaN(pos)) {
								pos = _outOfAuction;
							}
							double error = model.getPrediction(q, pos);
							if(Double.isNaN(error)) {
								error = 0.0;
							}
							double CPC = otherQueryReport.getCPC(q);
							if(Double.isNaN(CPC)) {
								if(_ignoreNan ) {
									continue;
								}
								CPC = 0.0;
							}
							error -= CPC;
							error = error*error;
							ourTotActual += CPC;
							ourTotError += error;
							ourTotErrorCounter++;
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		//		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	public void bidToPosPredictionChallenge(AbstractBidToPos baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			//			int numPromSlots = status.getSlotInfo().getPromotedSlots();
			//			BasicPosToPrClick posToPrClickModel = new BasicPosToPrClick(numPromSlots);
			//			AvgPosToPosDist avgPosToDistModel = new AvgPosToPosDist(40, numPromSlots, posToPrClickModel);

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractBidToPos model = (AbstractBidToPos) baseModel.getCopy();
				model.setNumPromSlots(status.getSlotInfo().getPromotedSlots());

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					model.updateModel(queryReport, salesReport, bidBundle);
					if(i >= 6) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double bid = otherBidBundle.getBid(q);
							if(bid != 0) {
								double avgPos = model.getPrediction(q, otherBidBundle.getBid(q));
								if(Double.isNaN(avgPos)) {
									avgPos = _outOfAuction;
								}
								double pos = otherQueryReport.getPosition(q);
								if(Double.isNaN(pos)) {
									if(_ignoreNan ) {
										continue;
									}
									pos = _outOfAuction;
								}
								double error = avgPos-pos;
								if(pos == _outOfAuction && avgPos > 5.0) {
									error = 0.0;
								}
								error = error*error;
								ourTotActual += pos;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		//		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	public void posToBidPredictionChallenge(AbstractBidToPos baseModel) throws IOException, ParseException {
		/*
		 * All these maps they are like this: <fileName<agentName,error>>
		 */
		HashMap<String,HashMap<String,Double>> ourTotErrorMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Double>> ourTotActualMegaMap = new HashMap<String,HashMap<String,Double>>();
		HashMap<String,HashMap<String,Integer>> ourTotErrorCounterMegaMap = new HashMap<String,HashMap<String,Integer>>();
		ArrayList<String> filenames = getGameStrings();
		for(int fileIdx = 0; fileIdx < filenames.size(); fileIdx++) {
			String filename = filenames.get(fileIdx);
			GameStatusHandler statusHandler = new GameStatusHandler(filename);
			GameStatus status = statusHandler.getGameStatus();
			String[] agents = status.getAdvertisers();

			//			int numPromSlots = status.getSlotInfo().getPromotedSlots();
			//			BasicPosToPrClick posToPrClickModel = new BasicPosToPrClick(numPromSlots);
			//			AvgPosToPosDist avgPosToDistModel = new AvgPosToPosDist(40, numPromSlots, posToPrClickModel);

			/*
			 * One map for each advertiser
			 */
			HashMap<String,Double> ourTotErrorMap = new HashMap<String, Double>();
			HashMap<String,Double> ourTotActualMap = new HashMap<String, Double>();
			HashMap<String,Integer> ourTotErrorCounterMap = new HashMap<String, Integer>();

			//Make the query space
			LinkedHashSet<Query> querySpace = new LinkedHashSet<Query>();
			querySpace.add(new Query(null, null));
			for(Product product : status.getRetailCatalog()) {
				// The F1 query classes
				// F1 Manufacturer only
				querySpace.add(new Query(product.getManufacturer(), null));
				// F1 Component only
				querySpace.add(new Query(null, product.getComponent()));

				// The F2 query class
				querySpace.add(new Query(product.getManufacturer(), product.getComponent()));
			}

			for(int agent = 0; agent < agents.length; agent++) {
				HashMap<String, AdvertiserInfo> advertiserInfos = status.getAdvertiserInfos();
				AdvertiserInfo advInfo = advertiserInfos.get(agents[agent]);
				AbstractBidToPos model = (AbstractBidToPos) baseModel.getCopy();
				model.setNumPromSlots(status.getSlotInfo().getPromotedSlots());

				AbstractPosToBid posToBid = null;
				try {
					posToBid = new BidToPosInverter(new RConnection(), querySpace, model, .05, 0, 3.0);
				} catch (RserveException e) {
					e.printStackTrace();
				}

				double ourTotError = 0;
				double ourTotActual = 0;
				int ourTotErrorCounter = 0;

				HashMap<String, LinkedList<SalesReport>> allSalesReports = status.getSalesReports();
				HashMap<String, LinkedList<QueryReport>> allQueryReports = status.getQueryReports();
				HashMap<String, LinkedList<BidBundle>> allBidBundles = status.getBidBundles();

				LinkedList<SalesReport> ourSalesReports = allSalesReports.get(agents[agent]);
				LinkedList<QueryReport> ourQueryReports = allQueryReports.get(agents[agent]);
				LinkedList<BidBundle> ourBidBundles = allBidBundles.get(agents[agent]);

				//				System.out.println(agents[agent]);
				for(int i = 0; i < 57; i++) {
					SalesReport salesReport = ourSalesReports.get(i);
					QueryReport queryReport = ourQueryReports.get(i);
					BidBundle bidBundle = ourBidBundles.get(i);

					model.updateModel(queryReport, salesReport, bidBundle);
					posToBid.updateModel(queryReport, salesReport, bidBundle);
					if(i >= 6) {
						/*
						 * Make Predictions and Evaluate Error Remember to do this for i + 2 !!!
						 */
						SalesReport otherSalesReport = ourSalesReports.get(i+2);
						QueryReport otherQueryReport = ourQueryReports.get(i+2);
						BidBundle otherBidBundle = ourBidBundles.get(i+2);
						for(Query q : querySpace) {
							double pos = otherQueryReport.getPosition(q);
							double error = posToBid.getPrediction(q, pos);
							if(Double.isNaN(error)) {
								error = 0.0;
							}
							double bid = otherBidBundle.getBid(q);
							//							System.out.println(q + "  Pos: " + pos + "  BidEst: " + error + "  bid: " + bid);
							if(bid != 0) {
								error -= bid;
								error = error*error;
								ourTotActual += bid;
								ourTotError += error;
								ourTotErrorCounter++;
							}
						}
					}
				}
				ourTotErrorMap.put(agents[agent],ourTotError);
				ourTotActualMap.put(agents[agent],ourTotActual);
				ourTotErrorCounterMap.put(agents[agent],ourTotErrorCounter);

			}

			ourTotErrorMegaMap.put(filename,ourTotErrorMap);
			ourTotActualMegaMap.put(filename,ourTotActualMap);
			ourTotErrorCounterMegaMap.put(filename,ourTotErrorCounterMap);
		}
		ArrayList<Double> RMSEList = new ArrayList<Double>();
		ArrayList<Double> actualList = new ArrayList<Double>();
		//		System.out.println("Model: " + baseModel);
		int dataPointCounter = 0;
		for(String file : filenames) {
			//			System.out.println("File: " + file);
			HashMap<String, Double> totErrorMap = ourTotErrorMegaMap.get(file);
			HashMap<String, Double> totActualMap = ourTotActualMegaMap.get(file);
			HashMap<String, Integer> totErrorCounterMap = ourTotErrorCounterMegaMap.get(file);
			for(String agent : totErrorCounterMap.keySet()) {
				//				System.out.println("\t Agent: " + agent);
				double totError = totErrorMap.get(agent);
				double totActual = totActualMap.get(agent);
				double totErrorCounter = totErrorCounterMap.get(agent);
				dataPointCounter += totErrorCounter;
				//				System.out.println("\t\t Predictions: " + totErrorCounter);
				double MSE = (totError/totErrorCounter);
				double RMSE = Math.sqrt(MSE);
				double actual = totActual/totErrorCounter;
				RMSEList.add(RMSE);
				actualList.add(actual);
			}
		}
		System.out.println("Data Points: " + dataPointCounter);
		double[] rmseStd = getStdDevAndMean(RMSEList);
		double[] actualStd = getStdDevAndMean(actualList);
		System.out.println(baseModel + ", " + rmseStd[0] + ", " + rmseStd[1] + ", " + actualStd[0] + ", " + actualStd[1]);
	}

	private double[] getStdDevAndMean(ArrayList<Double> list) {
		double n = list.size();
		double sum = 0.0;
		for(Double data : list) {
			sum += data;
		}
		double mean = sum/n;

		double variance = 0.0;

		for(Double data : list) {
			variance += (data-mean)*(data-mean);
		}

		variance /= (n-1);

		double[] stdDev = new double[2];
		stdDev[0] = mean;
		stdDev[1] = Math.sqrt(variance);
		return stdDev;
	}

	public static boolean intToBin(int x) {
		if(x == 0) {
			return false;
		}
		else if(x == 1) {
			return true;
		}
		else {
			throw new RuntimeException("intToBin can only be called with 0 or 1");
		}
	}

	public static void main(String[] args) throws RserveException {
		PredictionEvaluator evaluator = new PredictionEvaluator();
		Set<Query> _querySpace = new LinkedHashSet<Query>();
		_querySpace.add(new Query(null, null));
		_querySpace.add(new Query("lioneer", null));
		_querySpace.add(new Query(null, "tv"));
		_querySpace.add(new Query("lioneer", "tv"));
		_querySpace.add(new Query(null, "audio"));
		_querySpace.add(new Query("lioneer", "audio"));
		_querySpace.add(new Query(null, "dvd"));
		_querySpace.add(new Query("lioneer", "dvd"));
		_querySpace.add(new Query("pg", null));
		_querySpace.add(new Query("pg", "tv"));
		_querySpace.add(new Query("pg", "audio"));
		_querySpace.add(new Query("pg", "dvd"));
		_querySpace.add(new Query("flat", null));
		_querySpace.add(new Query("flat", "tv"));
		_querySpace.add(new Query("flat", "audio"));
		_querySpace.add(new Query("flat", "dvd"));

		try {
			double start = System.currentTimeMillis();
			//			model = new RegressionBidToPrClick(new RConnection(),_querySpace,false,2,20,new BasicTargetModel("flat", "tv"),true,false,false,false,false);
			//			model = new EnsembleBidToPrClick(_querySpace, 30, 3, new BasicTargetModel("flat", "tv"), false, true);
			//			evaluator.clickPrPredictionChallenge(model);
			RConnection _rConnection = new RConnection();
			BasicTargetModel _targModel = new BasicTargetModel(null, null);

			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,10,20,true,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,10,20,false,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,10,30,true,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,10,30,false,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,10,50,true,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,10,50,false,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,5,20,true,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,5,20,false,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,5,30,true,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,5,30,false,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,5,50,true,true));
			//			evaluator.bidToCPCPredictionChallenge(new EnsembleBidToCPC(_querySpace,5,50,false,true));

			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,10,20,true,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,10,20,false,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,10,30,true,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,10,30,false,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,10,50,true,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,10,50,false,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,5,20,true,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,5,20,false,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,5,30,true,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,5,30,false,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,5,50,true,true));
			//			evaluator.posToCPCPredictionChallenge(new EnsemblePosToCPC(_querySpace,5,50,false,true));

			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,10,20,_targModel,true,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,10,20,_targModel,false,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,10,30,_targModel,true,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,10,30,_targModel,false,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,10,50,_targModel,true,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,10,50,_targModel,false,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,5,20,_targModel,true,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,5,20,_targModel,false,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,5,30,_targModel,true,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,5,30,_targModel,false,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,5,50,_targModel,true,true));
			//			evaluator.bidToClickPrPredictionChallenge(new EnsembleBidToPrClick(_querySpace,5,50,_targModel,false,true));

			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,10,20,_targModel,true,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,10,20,_targModel,false,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,10,30,_targModel,true,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,10,30,_targModel,false,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,10,50,_targModel,true,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,10,50,_targModel,false,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,5,20,_targModel,true,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,5,20,_targModel,false,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,5,30,_targModel,true,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,5,30,_targModel,false,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,5,50,_targModel,true,true));
			//			evaluator.posToClickPrPredictionChallenge(new EnsemblePosToPrClick(_querySpace,5,50,_targModel,false,true));

			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)), 10, 20,true,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),10,20,false,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),10,30,true,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),10,30,false,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),10,50,true,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),10,50,false,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),5,20,true,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),5,20,false,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),5,30,true,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),5,30,false,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),5,50,true,true));
			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)),5,50,false,true));			

			//			evaluator.bidToPosToClickPrPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)), 10, 20,true,true), new EnsemblePosToPrClick(_querySpace,10,30,_targModel,true,true));

			//			evaluator.posToBidToCPCPredictionChallenge(new EnsembleBidToPos(_querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)), 10, 20,true,true), new EnsembleBidToCPC(_querySpace,10,20,true,true));


			evaluator.posToBidPredictionChallenge(new RegressionBidToPos(_rConnection, _querySpace, true, 1, 60, true, .815, false, false, false, false));
			double stop = System.currentTimeMillis();
			double elapsed = stop - start;
			System.out.println("This took " + (elapsed / 1000) + " seconds");
			evaluator.posToCPCToBidPredictionChallenge(new RegressionBidToCPC(_rConnection, _querySpace, true, 1, 60, true, .89, false, false, false, false,false,false), new RegressionPosToCPC(_rConnection, _querySpace, false, 1, 15, true, .74, false, false, true, false, false, false));

			//			evaluator.bidToPosPredictionChallenge(new EnsembleBidToPos(_querySpace,null, 10,30,true,true));

			//						evaluator.bidToCPCPredictionChallenge(new RegressionBidToCPC(_rConnection, _querySpace, false, 3, 30, true, .85, false, false, false, false,false,false));
			//			evaluator.posToCPCPredictionChallenge(new RegressionPosToCPC(_rConnection, _querySpace, false, 1, 30, true, .85, false, false, false, false, false, false));
			//			evaluator.bidToClickPrPredictionChallenge(new RegressionBidToPrClick(_rConnection, _querySpace, false, 1, 30, _targModel, true, .85, false, false, false, false));
			//			evaluator.posToClickPrPredictionChallenge(new RegressionPosToPrClick(_rConnection, _querySpace, false, 1, 30, _targModel, true, .85, false, false, false, false));
			//			evaluator.bidToPosPredictionChallenge(new RegressionBidToPos(_rConnection, _querySpace, false, 1, 30, true, .85, false, false, false, false));


			//			evaluator.bidToPosPredictionChallenge(new SpatialBidToPos(_rConnection, _querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)), false, 1, 30,true, .85));

			//			/*
			//			 * Test all BID-POS models!
			//			 */
			//			for(int perQuery = 0; perQuery < 2; perQuery++) {
			//				for(int IDVar = 1; IDVar < 5; IDVar++) {
			//					for(int numPrevDays = 15; numPrevDays <= 60; numPrevDays += 15) {
			//						for(int weighted = 0; weighted < 2; weighted++) {
			//							for(double mWeight = 0.74; mWeight < 1.0; mWeight += .075) {
			//								for(int queryIndicators = 0; queryIndicators < 2; queryIndicators++) {
			//									for(int queryTypeIndicators = 0; queryTypeIndicators < 1; queryTypeIndicators++) {
			//										for(int powers = 0; powers < 2; powers++) {
			//											for(int ignoreNaN = 0; ignoreNaN < 2; ignoreNaN++) {
			//												if(!(IDVar == 2) && !(queryIndicators == 1 && queryTypeIndicators == 1)
			//														&& !(perQuery == 1 && (queryIndicators == 1 || queryTypeIndicators == 1))
			//														&& !(intToBin(perQuery) && numPrevDays <= 30) && !(!intToBin(weighted) && mWeight > .74)) {
			//													AbstractBidToPos model = new RegressionBidToPos(_rConnection, _querySpace, intToBin(perQuery), IDVar, numPrevDays, intToBin(weighted), mWeight, intToBin(queryIndicators), intToBin(queryTypeIndicators), intToBin(powers), intToBin(ignoreNaN));
			//													evaluator.bidToPosPredictionChallenge(model);
			//													model = new SpatialBidToPos(_rConnection, _querySpace, new AvgPosToPosDist(40,1,new BasicPosToPrClick(1)), intToBin(ignoreNaN), IDVar, numPrevDays, intToBin(weighted), mWeight);
			//													evaluator.bidToPosPredictionChallenge(model);
			//													System.out.println(model);
			//													double stop = System.currentTimeMillis();
			//													double elapsed = stop - start;
			//													System.out.println("This took " + (elapsed / 1000) + " seconds");
			//													start = System.currentTimeMillis();
			//												}
			//											}
			//										}
			//									}
			//								}
			//							}
			//						}
			//					}
			//				}
			//			}



			//						/*
			//						 * Test all BID-PRCLICK models!
			//						 */
			//						for(int perQuery = 0; perQuery < 2; perQuery++) {
			//							for(int IDVar = 1; IDVar < 5; IDVar++) {
			//								for(int numPrevDays = 15; numPrevDays <= 60; numPrevDays += 15) {
			//									for(int weighted = 0; weighted < 2; weighted++) {
			//										for(double mWeight = 0.74; mWeight < 1.0; mWeight += .075) {
			//											for(int robust = 0; robust < 1; robust++) {
			//												for(int queryIndicators = 0; queryIndicators < 2; queryIndicators++) {
			//													for(int queryTypeIndicators = 0; queryTypeIndicators < 1; queryTypeIndicators++) {
			//														for(int powers = 0; powers < 2; powers++) {
			//															if(!(IDVar == 2) && !(robust == 1 && (queryIndicators == 1 || queryTypeIndicators == 1 || powers == 1)) && !(queryIndicators == 1 && queryTypeIndicators == 1)
			//																	&& !(perQuery == 1 && (queryIndicators == 1 || queryTypeIndicators == 1)) && !(intToBin(perQuery) && numPrevDays < 30) && !(!intToBin(weighted) && mWeight > .74)) {
			//																RegressionBidToPrClick model = new RegressionBidToPrClick(_rConnection, _querySpace, intToBin(perQuery), IDVar, numPrevDays, _targModel, intToBin(weighted), mWeight, intToBin(robust), intToBin(queryIndicators), intToBin(queryTypeIndicators), intToBin(powers));
			//																evaluator.bidToClickPrPredictionChallenge(model);
			//																//															System.out.println(model);
			//																double stop = System.currentTimeMillis();
			//																double elapsed = stop - start;
			//																System.out.println("This took " + (elapsed / 1000) + " seconds");
			//																start = System.currentTimeMillis();
			//															}
			//														}
			//													}
			//												}
			//											}
			//										}
			//									}
			//								}
			//							}
			//						}


			/*
//			 * Test all POS-PRCLICK models!
//			 */
			//			for(int perQuery = 0; perQuery < 2; perQuery++) {
			//				for(int IDVar = 1; IDVar < 5; IDVar++) {
			//					for(int numPrevDays = 15; numPrevDays <= 60; numPrevDays += 15) {
			//						for(int weighted = 0; weighted < 2; weighted++) {
			//							for(double mWeight = 0.64; mWeight < 1.0; mWeight += .075) {
			//								for(int robust = 0; robust < 1; robust++) {
			//									for(int queryIndicators = 0; queryIndicators < 2; queryIndicators++) {
			//										for(int queryTypeIndicators = 0; queryTypeIndicators < 1; queryTypeIndicators++) {
			//											for(int powers = 0; powers < 2; powers++) {
			//												if(!(IDVar == 2) && !(robust == 1 && (queryIndicators == 1 || queryTypeIndicators == 1 || powers == 1)) && !(queryIndicators == 1 && queryTypeIndicators == 1)
			//														&& !(perQuery == 1 && (queryIndicators == 1 || queryTypeIndicators == 1)) && !(intToBin(perQuery) && numPrevDays < 30) && !(!intToBin(weighted) && mWeight > .74)) {
			//													RegressionPosToPrClick model = new RegressionPosToPrClick(_rConnection, _querySpace, intToBin(perQuery), IDVar, numPrevDays, _targModel, intToBin(weighted), mWeight, intToBin(robust), intToBin(queryIndicators), intToBin(queryTypeIndicators), intToBin(powers));
			//													evaluator.posToClickPrPredictionChallenge(model);
			//													//															System.out.println(model);
			//													double stop = System.currentTimeMillis();
			//													double elapsed = stop - start;
			//													System.out.println("This took " + (elapsed / 1000) + " seconds");
			//													start = System.currentTimeMillis();
			//												}
			//											}
			//										}
			//									}
			//								}
			//							}
			//						}
			//					}
			//				}
			//			}

			//
			//			/*
			//			 * Test all POS-CPC models!
			//			 */
			//			for(int perQuery = 0; perQuery < 2; perQuery++) {
			//				for(int IDVar = 1; IDVar < 5; IDVar++) {
			//					for(int numPrevDays = 15; numPrevDays <= 60; numPrevDays += 15) {
			//						for(int weighted = 0; weighted < 2; weighted++) {
			//							for(double mWeight = 0.74; mWeight < 1.0; mWeight += .075) {
			//								for(int robust = 0; robust < 1; robust++) {
			//									for(int loglinear = 0; loglinear < 1; loglinear++) {
			//										for(int queryIndicators = 0; queryIndicators < 2; queryIndicators++) {
			//											for(int queryTypeIndicators = 0; queryTypeIndicators < 1; queryTypeIndicators++) {
			//												for(int powers = 0; powers < 2; powers++) {
			//													for(int ignoreNaN = 0; ignoreNaN < 2; ignoreNaN++) {
			//														if(!(IDVar == 2) && !(robust == 1 && (queryIndicators == 1 || queryTypeIndicators == 1 || powers == 1)) && !(queryIndicators == 1 && queryTypeIndicators == 1)
			//																&& !(perQuery == 1 && (queryIndicators == 1 || queryTypeIndicators == 1)) && !(intToBin(perQuery) && numPrevDays < 30) && !(!intToBin(weighted) && mWeight > .74)) {
			//															RegressionPosToCPC model = new RegressionPosToCPC(_rConnection, _querySpace, intToBin(perQuery), IDVar, numPrevDays, intToBin(weighted), mWeight, intToBin(robust), intToBin(loglinear), intToBin(queryIndicators), intToBin(queryTypeIndicators), intToBin(powers), intToBin(ignoreNaN));
			//															evaluator.posToCPCPredictionChallenge(model);
			//															//															System.out.println(model);
			//															double stop = System.currentTimeMillis();
			//															double elapsed = stop - start;
			//															System.out.println("This took " + (elapsed / 1000) + " seconds");
			//															start = System.currentTimeMillis();
			//														}
			//													}
			//												}
			//											}
			//										}
			//									}
			//								}
			//							}
			//						}
			//					}
			//				}
			//			}



			//			/*
			//			 * Test all BID-CPC models!
			//			 */
			//			for(int perQuery = 0; perQuery < 2; perQuery++) {
			//				for(int IDVar = 1; IDVar < 5; IDVar++) {
			//					for(int numPrevDays = 60; numPrevDays <= 60; numPrevDays += 15) {
			//						for(int weighted = 0; weighted < 2; weighted++) {
			//							for(double mWeight = 0.74; mWeight < 1.0; mWeight += .075) {
			//								for(int robust = 0; robust < 1; robust++) {
			//									for(int loglinear = 0; loglinear < 1; loglinear++) {
			//										for(int queryIndicators = 0; queryIndicators < 2; queryIndicators++) {
			//											for(int queryTypeIndicators = 0; queryTypeIndicators < 1; queryTypeIndicators++) {
			//												for(int powers = 0; powers < 2; powers++) {
			//													for(int ignoreNaN = 0; ignoreNaN < 2; ignoreNaN++) {
			//														if(!(IDVar == 2) && !(robust == 1 && (queryIndicators == 1 || queryTypeIndicators == 1 || powers == 1)) && !(queryIndicators == 1 && queryTypeIndicators == 1)
			//																&& !(perQuery == 1 && (queryIndicators == 1 || queryTypeIndicators == 1)) && !(intToBin(perQuery) && numPrevDays < 30) && !(!intToBin(weighted) && mWeight > .74)) {
			//															AbstractBidToCPC model = new RegressionBidToCPC(_rConnection, _querySpace, intToBin(perQuery), IDVar, numPrevDays, intToBin(weighted), mWeight, intToBin(robust), intToBin(loglinear), intToBin(queryIndicators), intToBin(queryTypeIndicators), intToBin(powers), intToBin(ignoreNaN));
			//															evaluator.bidToCPCPredictionChallenge(model);
			//															//															System.out.println(model);
			//															double stop = System.currentTimeMillis();
			//															double elapsed = stop - start;
			//															System.out.println("This took " + (elapsed / 1000) + " seconds");
			//															start = System.currentTimeMillis();
			//														}
			//													}
			//												}
			//											}
			//										}
			//									}
			//								}
			//							}
			//						}
			//					}
			//				}
			//			}

			//			for(double c = 0.0; c <= 1.01; c += .01) {
			//				ConstantBidToCPC model = new ConstantBidToCPC(c);
			//				evaluator.bidToCPCPredictionChallenge(model);
			//				//															System.out.println(model);
			//				double stop = System.currentTimeMillis();
			//				double elapsed = stop - start;
			//				System.out.println("This took " + (elapsed / 1000) + " seconds");
			//				start = System.currentTimeMillis();
			//			}

			stop = System.currentTimeMillis();
			elapsed = stop - start;
			System.out.println("This took " + (elapsed / 1000) + " seconds");

		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
}