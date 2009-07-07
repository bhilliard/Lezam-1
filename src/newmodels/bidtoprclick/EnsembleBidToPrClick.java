package newmodels.bidtoprclick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

import org.jfree.base.modules.DefaultModuleInfo;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import edu.umich.eecs.tac.props.Ad;
import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.QueryType;

public class EnsembleBidToPrClick extends AbstractBidToPrClick {

	protected Set<Query> _querySpace;

	AbstractBidToPrClick _defaultModel;

	/*
	 * Model Type I
	 * 	-These models predict for all queries
	 */
	protected HashMap<String,AbstractBidToPrClick> _typeIModels;
	protected HashMap<String,AbstractBidToPrClick> _typeIUsableModels;
	protected HashMap<String,HashMap<Query,LinkedList<Double>>> _typeIDailyModelError;
	protected HashMap<BidBundle,HashMap<String,HashMap<Query,Double>>> _typeIPredictions;

	/*
	 * Model Type II
	 * 	-These models predict by query type
	 */
	protected HashMap<QueryType,HashMap<String,AbstractBidToPrClick>> _typeIIModels;
	protected HashMap<QueryType,HashMap<String,AbstractBidToPrClick>> _typeIIUsableModels;
	protected HashMap<QueryType,HashMap<String,HashMap<Query,LinkedList<Double>>>> _typeIIDailyModelError;
	protected HashMap<BidBundle,HashMap<QueryType,HashMap<String,HashMap<Query,Double>>>> _typeIIPredictions;

	/*
	 * Model Type II
	 * 	-These models only predict for one query
	 */
	protected HashMap<Query,HashMap<String,AbstractBidToPrClick>> _typeIIIModels;
	protected HashMap<Query,HashMap<String,AbstractBidToPrClick>> _typeIIIUsableModels;
	protected HashMap<Query,HashMap<String,LinkedList<Double>>> _typeIIIDailyModelError;
	protected HashMap<BidBundle,HashMap<Query,HashMap<String,Double>>> _typeIIIPredictions;

	/*
	 * This holds the ensemble used for predicting
	 */
	protected HashMap<Query,LinkedList<AbstractBidToPrClick>> _ensemble;
	protected HashMap<Query,LinkedList<Double>> _ensembleError;
	protected HashMap<BidBundle,HashMap<Query,Double>> _ensemblePredictions;
	protected HashMap<Query,HashMap<String,Integer>> _ensembleMembers;

	/*
	 * Constants for making ensemble
	 */
	protected int NUMPASTDAYS = 3;
	protected int ENSEMBLESIZE = 12;

	private RConnection rConnection;



	public EnsembleBidToPrClick(Set<Query> querySpace) {
		_querySpace = querySpace;

		/*
		 * Initialize Type I Models
		 */
		_typeIModels = new HashMap<String, AbstractBidToPrClick>();
		_typeIUsableModels = new HashMap<String,AbstractBidToPrClick>();
		_typeIDailyModelError = new HashMap<String, HashMap<Query,LinkedList<Double>>>();
		_typeIPredictions = new HashMap<BidBundle, HashMap<String,HashMap<Query,Double>>>();

		/*
		 * Initialize Type II Models
		 */
		_typeIIModels = new HashMap<QueryType, HashMap<String,AbstractBidToPrClick>>();
		_typeIIUsableModels = new HashMap<QueryType, HashMap<String,AbstractBidToPrClick>>();
		_typeIIDailyModelError = new HashMap<QueryType, HashMap<String,HashMap<Query,LinkedList<Double>>>>();
		for(QueryType queryType : QueryType.values()) {
			HashMap<String, AbstractBidToPrClick> typeIIModels = new HashMap<String, AbstractBidToPrClick>();
			HashMap<String, AbstractBidToPrClick> typeIIUsableModels = new HashMap<String,AbstractBidToPrClick>();
			HashMap<String, HashMap<Query, LinkedList<Double>>> typeIIDailyModelError = new HashMap<String, HashMap<Query,LinkedList<Double>>>();
			_typeIIModels.put(queryType, typeIIModels);
			_typeIIUsableModels.put(queryType,typeIIUsableModels);
			_typeIIDailyModelError.put(queryType,typeIIDailyModelError);
		}
		_typeIIPredictions = new HashMap<BidBundle, HashMap<QueryType,HashMap<String,HashMap<Query,Double>>>>();

		/*
		 * Initialize Type III Models
		 */
		_typeIIIModels = new HashMap<Query, HashMap<String,AbstractBidToPrClick>>();
		_typeIIIUsableModels = new HashMap<Query, HashMap<String,AbstractBidToPrClick>>();
		_typeIIIDailyModelError = new HashMap<Query, HashMap<String,LinkedList<Double>>>();
		for(Query query : _querySpace) {
			HashMap<String, AbstractBidToPrClick> typeIIIModels = new HashMap<String, AbstractBidToPrClick>();
			HashMap<String, AbstractBidToPrClick> typeIIIUsableModels = new HashMap<String,AbstractBidToPrClick>();
			HashMap<String, LinkedList<Double>> typeIIIDailyModelError = new HashMap<String, LinkedList<Double>>();
			_typeIIIModels.put(query, typeIIIModels);
			_typeIIIUsableModels.put(query,typeIIIUsableModels);
			_typeIIIDailyModelError.put(query,typeIIIDailyModelError);
		}
		_typeIIIPredictions = new HashMap<BidBundle, HashMap<Query,HashMap<String,Double>>>();

		/*
		 * Initialize Ensemble
		 */
		_ensemble = new HashMap<Query, LinkedList<AbstractBidToPrClick>>();
		_ensembleError = new HashMap<Query, LinkedList<Double>>();
		for(Query query : _querySpace) {
			LinkedList<AbstractBidToPrClick> queryEnsemble = new LinkedList<AbstractBidToPrClick>();
			LinkedList<Double> queryEnsembleError = new LinkedList<Double>();
			_ensemble.put(query, queryEnsemble);
			_ensembleError.put(query, queryEnsembleError);
		}
		_ensemblePredictions = new HashMap<BidBundle, HashMap<Query,Double>>();

		_ensembleMembers = new HashMap<Query, HashMap<String,Integer>>();
		for(Query query : _querySpace) {
			HashMap<String,Integer> ensembleMember = new HashMap<String, Integer>();
			_ensembleMembers.put(query, ensembleMember);
		}

		try {
			rConnection = new RConnection();
		} catch (RserveException e) {
			e.printStackTrace();
		}
	}

	public void initializeEnsemble() {
		/*
		 * Add Type I Models
		 */
		String basename = "typeI";
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 6; j++) {
				AbstractBidToPrClick model = new TypeIRegressionBidToPrClick(rConnection,_querySpace, 2+i, 10*(j+1), false, false, false);
				addTypeIModel(basename + "_" + i + "_" + j +"_f_f_f", model);
				model = new TypeIRegressionBidToPrClick(rConnection,_querySpace, 2+i, 10*(j+1), true, false, false);
				addTypeIModel(basename + "_" + i + "_" + j +"_t_f_f", model);
				if(i == 0 && j == 1) {
					_defaultModel = model;
				}
				model = new TypeIRegressionBidToPrClick(rConnection,_querySpace, 2+i, 10*(j+1), false, true, false);
				addTypeIModel(basename + "_" + i + "_" + j +"_f_t_f", model);
				//					model = new TypeIRegressionBidToPrClick(rConnection,_querySpace, 2+i, 10*(j+1), false, false, true);
				//					addTypeIModel(basename + "_" + i + "_" + j +"_f_f_t", model);
				//					model = new TypeIRegressionBidToPrClick(rConnection,_querySpace, 2+i, 10*(j+1), true, false, true);
				//					addTypeIModel(basename + "_" + i + "_" + j +"_t_f_t", model);
				//					model = new TypeIRegressionBidToPrClick(rConnection,_querySpace, 2+i, 10*(j+1), false, true, true);
				//					addTypeIModel(basename + "_" + i + "_" + j +"_f_t_t", model);
			}
		}

		/*
		 * Add Type II Models
		 */
		basename = "typeII";
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 6; j++) {
				AbstractBidToPrClick model = new TypeIIRegressionBidToPrClick(rConnection, _querySpace,QueryType.FOCUS_LEVEL_ZERO, 2+i, 10*(j+1), false);
				addTypeIIModel(QueryType.FOCUS_LEVEL_ZERO,basename + "_" + i + "_" + j +"_F0_f", model);
				//					model = new TypeIIRegressionBidToPrClick(rConnection, _querySpace,QueryType.FOCUS_LEVEL_ZERO, 2+i, 10*(j+1), true);
				//					addTypeIIModel(QueryType.FOCUS_LEVEL_ZERO,basename + "_" + i + "_" + j +"_F0_t", model);

				model = new TypeIIRegressionBidToPrClick(rConnection, _querySpace,QueryType.FOCUS_LEVEL_ONE, 2+i, 10*(j+1), false);
				addTypeIIModel(QueryType.FOCUS_LEVEL_ONE,basename + "_" + i + "_" + j +"_F1_f", model);
				//					model = new TypeIIRegressionBidToPrClick(rConnection, _querySpace,QueryType.FOCUS_LEVEL_ONE, 2+i, 10*(j+1), true);
				//					addTypeIIModel(QueryType.FOCUS_LEVEL_ONE,basename + "_" + i + "_" + j +"_F1_t", model);

				model = new TypeIIRegressionBidToPrClick(rConnection, _querySpace,QueryType.FOCUS_LEVEL_TWO, 2+i, 10*(j+1), false);
				//					addTypeIIModel(QueryType.FOCUS_LEVEL_TWO,basename + "_" + i + "_" + j +"_F2_f", model);
				//					model = new TypeIIRegressionBidToPrClick(rConnection, _querySpace,QueryType.FOCUS_LEVEL_TWO, 2+i, 10*(j+1), true);
				//					addTypeIIModel(QueryType.FOCUS_LEVEL_TWO,basename + "_" + i + "_" + j +"_F2_t", model);
			}
		}

		/*
		 * Ad Type III Models
		 */
		basename = "typeIII";
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 6; j++) {
				for(Query query : _querySpace) {
					AbstractBidToPrClick model = new TypeIIIRegressionBidToPrClick(rConnection, _querySpace,query, 2+i, 10*(j+1), false);
					addTypeIIIModel(query,basename + "_" + i + "_" + j +"_" + query.getManufacturer() + "_" + query.getComponent() + "_f", model);
					//						model = new TypeIIIRegressionBidToPrClick(rConnection, _querySpace,query, 2+i, 10*(j+1), true);
					//						addTypeIIIModel(query,basename + "_" + i + "_" + j +"_" + query.getManufacturer() + "_" + query.getComponent() + "_t", model);
				}
			}
		}
	}

	public void addTypeIModel(String name, AbstractBidToPrClick model) {
		_typeIModels.put(name,model);
		HashMap<Query, LinkedList<Double>> dailyModelError = new HashMap<Query,LinkedList<Double>>();
		for(Query query : _querySpace) {
			LinkedList<Double> dailyModelErrorList = new LinkedList<Double>();
			dailyModelError.put(query,dailyModelErrorList);
		}
		_typeIDailyModelError.put(name,dailyModelError);
	}


	public void addTypeIIModel(QueryType queryType,String name, AbstractBidToPrClick model) {
		HashMap<String, AbstractBidToPrClick> typeIIModels = _typeIIModels.get(queryType);
		typeIIModels.put(name, model);
		_typeIIModels.put(queryType,typeIIModels);

		HashMap<Query, LinkedList<Double>> dailyModelError = new HashMap<Query,LinkedList<Double>>();
		for(Query query : _querySpace) {
			LinkedList<Double> dailyModelErrorList = new LinkedList<Double>();
			dailyModelError.put(query,dailyModelErrorList);
		}
		HashMap<String, HashMap<Query, LinkedList<Double>>> typeIIDailyModelError = _typeIIDailyModelError.get(queryType);
		typeIIDailyModelError.put(name,dailyModelError);
		_typeIIDailyModelError.put(queryType, typeIIDailyModelError);
	}

	public void addTypeIIIModel(Query query, String name, AbstractBidToPrClick model) {
		HashMap<String, AbstractBidToPrClick> typeIIIModels = _typeIIIModels.get(query);
		typeIIIModels.put(name, model);
		_typeIIIModels.put(query,typeIIIModels);

		HashMap<String, LinkedList<Double>> typeIIIDailyModelError = _typeIIIDailyModelError.get(query);
		LinkedList<Double> dailyModelErrorList = new LinkedList<Double>();
		typeIIIDailyModelError.put(name, dailyModelErrorList);
		_typeIIIDailyModelError.put(query, typeIIIDailyModelError);
	}


	@Override
	public boolean updateModel(QueryReport queryreport, BidBundle bidbundle) {

		boolean ensembleUsable = false;

		/*
		 * Update Type I Models
		 */
		_typeIUsableModels = new HashMap<String, AbstractBidToPrClick>();
		for(String name : _typeIModels.keySet()) {
			AbstractBidToPrClick model = _typeIModels.get(name);
			boolean usable = model.updateModel(queryreport, bidbundle);
			if(usable) {
				_typeIUsableModels.put(name, model);
				ensembleUsable = true;
			}
		}

		/*
		 * Update Type II Models
		 */
		_typeIIUsableModels = new HashMap<QueryType, HashMap<String,AbstractBidToPrClick>>();
		for(QueryType queryType : QueryType.values()) {
			HashMap<String, AbstractBidToPrClick> typeIIModels = _typeIIModels.get(queryType);
			HashMap<String, AbstractBidToPrClick> typeIIUsableModels = new HashMap<String,AbstractBidToPrClick>();
			for(String name : typeIIModels.keySet()) {
				AbstractBidToPrClick model = typeIIModels.get(name);
				boolean usable = model.updateModel(queryreport, bidbundle);
				if(usable) {
					typeIIUsableModels.put(name, model);
					ensembleUsable = true;
				}
			}
			_typeIIUsableModels.put(queryType, typeIIUsableModels);
		}

		/*
		 * Update Type III Models
		 */
		_typeIIIUsableModels = new HashMap<Query, HashMap<String,AbstractBidToPrClick>>();
		for(Query query : _querySpace) {
			HashMap<String, AbstractBidToPrClick> typeIIIModels = _typeIIIModels.get(query);
			HashMap<String, AbstractBidToPrClick> typeIIIUsableModels = new HashMap<String,AbstractBidToPrClick>();
			for(String name : typeIIIModels.keySet()) {
				AbstractBidToPrClick model = typeIIIModels.get(name);
				boolean usable = model.updateModel(queryreport, bidbundle);
				if(usable) {
					typeIIIUsableModels.put(name, model);
					ensembleUsable = true;
				}
			}
			_typeIIIUsableModels.put(query, typeIIIUsableModels);
		}

		return ensembleUsable;
	}

	public void createEnsemble() {

		/*
		 * Initialize Error Mappings
		 */
		HashMap<Query,ArrayList<ModelErrorPair>> modelErrorMap = new HashMap<Query, ArrayList<ModelErrorPair>>();
		for(Query query : _querySpace) {
			ArrayList<ModelErrorPair> modelErrorPairList = new ArrayList<ModelErrorPair>();
			modelErrorMap.put(query, modelErrorPairList);
		}

		/*
		 * Add Type I Models
		 */
		for(String name : _typeIUsableModels.keySet()) {
			AbstractBidToPrClick model = _typeIUsableModels.get(name);
			HashMap<Query, LinkedList<Double>> typeIDailyModelError = _typeIDailyModelError.get(name);
			for(Query query : _querySpace) {
				ArrayList<ModelErrorPair> modelErrorPairList = modelErrorMap.get(query);
				double error = 0;
				LinkedList<Double> dailyModelError = typeIDailyModelError.get(query);
				double bound = NUMPASTDAYS;
				if(dailyModelError.size() < NUMPASTDAYS) {
					bound = dailyModelError.size();
				}
				for(int i = 0; i < bound; i++) {
					error += dailyModelError.get(dailyModelError.size() - 1 - i);
				}
				error/= bound;
				if(!Double.isNaN(error)) {
					ModelErrorPair modelErrorPair = new ModelErrorPair(name,model,error);
					modelErrorPairList.add(modelErrorPair);
					while(modelErrorPairList.size() > ENSEMBLESIZE) {
						Collections.sort(modelErrorPairList);
						modelErrorPairList.remove(modelErrorPairList.size()-1);
					}
				}
			}
		}

		/*
		 * Add Type II Models
		 */

		for(QueryType queryType : QueryType.values()) {
			HashMap<String, HashMap<Query, LinkedList<Double>>> queryTypeDailyError = _typeIIDailyModelError.get(queryType);
			HashMap<String, AbstractBidToPrClick> typeIIUsableModels = _typeIIUsableModels.get(queryType);
			for(String name : typeIIUsableModels.keySet()) {
				AbstractBidToPrClick model = typeIIUsableModels.get(name);
				HashMap<Query, LinkedList<Double>> typeIIDailyModelError = queryTypeDailyError.get(name);
				for(Query query : _querySpace) {
					if(query.getType() == queryType) {
						ArrayList<ModelErrorPair> modelErrorPairList = modelErrorMap.get(query);
						double error = 0;
						LinkedList<Double> dailyModelError = typeIIDailyModelError.get(query);
						int bound = NUMPASTDAYS;
						if(dailyModelError.size() < NUMPASTDAYS) {
							bound = dailyModelError.size();
						}
						for(int i = 0; i < bound; i++) {
							error += dailyModelError.get(dailyModelError.size() - 1 - i);
						}
						error/= bound;
						if(!Double.isNaN(error)) {
							ModelErrorPair modelErrorPair = new ModelErrorPair(name,model,error);
							modelErrorPairList.add(modelErrorPair);
							while(modelErrorPairList.size() > ENSEMBLESIZE) {
								Collections.sort(modelErrorPairList);
								modelErrorPairList.remove(modelErrorPairList.size()-1);
							}
						}
					}
				}
			}
		}

		/*
		 * Add Type III Models
		 */

		for(Query query : _querySpace) {
			HashMap<String, LinkedList<Double>> typeIIIDailyModelError = _typeIIIDailyModelError.get(query);
			HashMap<String, AbstractBidToPrClick> typeIIIUsableModels = _typeIIIUsableModels.get(query);
			for(String name : typeIIIUsableModels.keySet()) {
				AbstractBidToPrClick model = typeIIIUsableModels.get(name);
				ArrayList<ModelErrorPair> modelErrorPairList = modelErrorMap.get(query);
				double error = 0;
				LinkedList<Double> dailyModelError = typeIIIDailyModelError.get(name);
				int bound = NUMPASTDAYS;
				if(dailyModelError.size() < NUMPASTDAYS) {
					bound = dailyModelError.size();
				}
				for(int i = 0; i < bound; i++) {
					error += dailyModelError.get(dailyModelError.size() - 1 - i);
				}
				error/= bound;
				if(!Double.isNaN(error)) {
					ModelErrorPair modelErrorPair = new ModelErrorPair(name,model,error);
					modelErrorPairList.add(modelErrorPair);
					while(modelErrorPairList.size() > ENSEMBLESIZE) {
						Collections.sort(modelErrorPairList);
						modelErrorPairList.remove(modelErrorPairList.size()-1);
					}
				}
			}
		}

		/*
		 * Initialize Ensemble
		 */
		_ensemble = new HashMap<Query, LinkedList<AbstractBidToPrClick>>();
		for(Query query: _querySpace) {
			LinkedList<AbstractBidToPrClick> queryEnsemble = new LinkedList<AbstractBidToPrClick>();
			ArrayList<ModelErrorPair> modelErrorPairList = modelErrorMap.get(query);
			for(ModelErrorPair modelErrorPair : modelErrorPairList) {
				//				System.out.println("Query: " + query +"  Name: " + modelErrorPair.getName() + "  Error: " + modelErrorPair.getError());
				queryEnsemble.add(modelErrorPair.getModel());
				HashMap<String, Integer> ensembleMembers = _ensembleMembers.get(query);
				Integer ensembleUseCount = ensembleMembers.get(modelErrorPair.getName());
				if(ensembleUseCount == null) {
					ensembleMembers.put(modelErrorPair.getName(), 1);
				}
				else {
					ensembleMembers.put(modelErrorPair.getName(), ensembleUseCount+1);
				}
			}
			_ensemble.put(query, queryEnsemble);
		}

	}

	public void updatePredictions(BidBundle bundle) {

		/*
		 * Update Type I Predictions
		 */
		HashMap<String,HashMap<Query,Double>> typeIPredictions = new HashMap<String, HashMap<Query,Double>>();
		for(String name : _typeIUsableModels.keySet()) {
			AbstractBidToPrClick model = _typeIUsableModels.get(name);
			HashMap<Query,Double> predictions = new HashMap<Query, Double>();
			for(Query query : _querySpace) {
				predictions.put(query, model.getPrediction(query, bundle.getBid(query), new Ad()));
			}
			typeIPredictions.put(name, predictions);
		}
		_typeIPredictions.put(bundle, typeIPredictions);


		/*
		 * Update Type II Predictions
		 */
		HashMap<QueryType,HashMap<String,HashMap<Query,Double>>> typeIIPredictions = new HashMap<QueryType,HashMap<String,HashMap<Query,Double>>>();
		for(QueryType queryType : QueryType.values()) {
			HashMap<String, HashMap<Query, Double>> queryTypePredictions = new HashMap<String,HashMap<Query,Double>>();
			HashMap<String, AbstractBidToPrClick> typeIIUsableModels = _typeIIUsableModels.get(queryType);
			for(String name : typeIIUsableModels.keySet()) {
				AbstractBidToPrClick model = typeIIUsableModels.get(name);
				HashMap<Query,Double> predictions = new HashMap<Query, Double>();
				for(Query query : _querySpace) {
					if(queryType == query.getType()) {
						predictions.put(query, model.getPrediction(query, bundle.getBid(query), new Ad()));
					}
				}
				queryTypePredictions.put(name, predictions);
			}
			typeIIPredictions.put(queryType, queryTypePredictions);
		}
		_typeIIPredictions.put(bundle, typeIIPredictions);


		/*
		 * Update Type III Predictions
		 */
		HashMap<Query, HashMap<String, Double>> typeIIIPredictions = new HashMap<Query,HashMap<String,Double>>();
		for(Query query : _querySpace) {
			HashMap<String, AbstractBidToPrClick> typeIIIUsableModels = _typeIIIUsableModels.get(query);
			HashMap<String, Double> queryPredictions = new HashMap<String, Double>();
			for(String name : typeIIIUsableModels.keySet()) {
				AbstractBidToPrClick model = typeIIIUsableModels.get(name);
				queryPredictions.put(name, model.getPrediction(query, bundle.getBid(query), new Ad()));
			}
			typeIIIPredictions.put(query, queryPredictions);
		}
		_typeIIIPredictions.put(bundle, typeIIIPredictions);

		/*
		 * Update Ensemble Predictions
		 */
		HashMap<Query,Double> ensemblePredictions = new HashMap<Query, Double>();
		for(Query query : _querySpace) {
			double prediction = 0.0;
			LinkedList<AbstractBidToPrClick> queryEnsemble = _ensemble.get(query);
			for(AbstractBidToPrClick model : queryEnsemble) {
				prediction += model.getPrediction(query, bundle.getBid(query), new Ad());
			}
			prediction /= queryEnsemble.size();
			ensemblePredictions.put(query, prediction);
		}
		_ensemblePredictions.put(bundle, ensemblePredictions);
	}


	public void updateError(QueryReport queryReport, BidBundle bundle) {

		/*
		 * Update Type I Error
		 */
		HashMap<String, HashMap<Query, Double>> typeIPredictions = _typeIPredictions.get(bundle);
		for(String name : _typeIModels.keySet()) {
			HashMap<Query, LinkedList<Double>> typeIDailyModelError = _typeIDailyModelError.get(name);
			HashMap<Query, Double> predictions = typeIPredictions.get(name);
			for(Query query : _querySpace) {
				LinkedList<Double> queryDailyError = typeIDailyModelError.get(query);
				if(predictions != null) {
					double error = predictions.get(query);
					int imps = queryReport.getImpressions(query);
					int clicks = queryReport.getClicks(query);
					if(!(imps == 0 || clicks == 0)) {
						error -= clicks/imps;
					}
					error = error*error;
					queryDailyError.add(error);
				}
				else {
					queryDailyError.add(Double.NaN);
				}
				typeIDailyModelError.put(query, queryDailyError);
			}
			_typeIDailyModelError.put(name, typeIDailyModelError);
		}

		/*
		 * Update Type II Error
		 */
		HashMap<QueryType, HashMap<String, HashMap<Query, Double>>> typeIIPredictions = _typeIIPredictions.get(bundle);
		for(QueryType queryType : QueryType.values()) {
			HashMap<String, HashMap<Query, Double>> queryTypePredictions = typeIIPredictions.get(queryType);
			HashMap<String, HashMap<Query, LinkedList<Double>>> typeIIDailyModelError = _typeIIDailyModelError.get(queryType);
			for(String name : _typeIIModels.get(queryType).keySet()) {
				HashMap<Query, Double> predictions = queryTypePredictions.get(name);
				HashMap<Query, LinkedList<Double>> dailyError = typeIIDailyModelError.get(name);
				for(Query query : _querySpace) {
					if(queryType == query.getType()) {
						LinkedList<Double> queryDailyError = dailyError.get(query);
						if(predictions != null) {
							double error = predictions.get(query);
							int imps = queryReport.getImpressions(query);
							int clicks = queryReport.getClicks(query);
							if(!(imps == 0 || clicks == 0)) {
								error -= clicks/imps;
							}
							error = error*error;
							queryDailyError.add(error);
						}
						else {
							queryDailyError.add(Double.NaN);
						}
						dailyError.put(query, queryDailyError);
					}
				}
				typeIIDailyModelError.put(name, dailyError);
			}
			_typeIIDailyModelError.put(queryType, typeIIDailyModelError);
		}


		/*
		 * Update Type III Error
		 */
		HashMap<Query, HashMap<String, Double>> typeIIIPredictions = _typeIIIPredictions.get(bundle);
		for(Query query : _querySpace) {
			HashMap<String, Double> predictions = typeIIIPredictions.get(query);
			HashMap<String, LinkedList<Double>> typeIIIDailyModelError = _typeIIIDailyModelError.get(query);
			for(String name : _typeIIIModels.get(query).keySet()) {
				Double prediction = predictions.get(name);
				LinkedList<Double> queryDailyError = typeIIIDailyModelError.get(name);
				if(prediction != null) {
					double error = prediction;
					int imps = queryReport.getImpressions(query);
					int clicks = queryReport.getClicks(query);
					if(!(imps == 0 || clicks == 0)) {
						error -= clicks/imps;
					}
					error = error*error;
					queryDailyError.add(error);
				}
				else {
					queryDailyError.add(Double.NaN);
				}
				typeIIIDailyModelError.put(name, queryDailyError);
			}
			_typeIIIDailyModelError.put(query, typeIIIDailyModelError);
		}


		/*
		 * Update Ensemble Error
		 */
		HashMap<Query, Double> ensemblePredictions = _ensemblePredictions.get(bundle);
		for(Query query : _querySpace) {
			LinkedList<Double> queryEnsembleError = _ensembleError.get(query);
			double error = ensemblePredictions.get(query);
			int imps = queryReport.getImpressions(query);
			int clicks = queryReport.getClicks(query);
			if(!(imps == 0 || clicks == 0)) {
				error -= clicks/imps;
			}
			error = error*error;
			queryEnsembleError.add(error);
			_ensembleError.put(query, queryEnsembleError);
		}
	}

	@Override
	public double getPrediction(Query query, double bid, Ad currentAd) {
		double prediction = 0.0;
		if(bid == 0) {
			return prediction;
		}
		LinkedList<AbstractBidToPrClick> queryEnsemble = _ensemble.get(query);
		if(queryEnsemble.size() == 0) {
			return _defaultModel.getPrediction(query, bid, currentAd);
		}
		int nancounter = 0;
		for(AbstractBidToPrClick model : queryEnsemble) {
			double pred = model.getPrediction(query, bid, currentAd);
			if(Double.isNaN(pred)) {
				nancounter++;
			}
			else {
				prediction += model.getPrediction(query, bid, currentAd);
			}
		}
		if(nancounter != 0) {
			//			System.out.println("\n\n\n\n\n CLICKPRnancounter: " + nancounter + "\n\n\n\n");
		}
		prediction /= (queryEnsemble.size()-nancounter);
		return prediction;
	}

	public void printEnsembleMemberSummary() {
		double tot = 0;
		for(Query query : _querySpace) {
			HashMap<String, Integer> ensembleMembers = _ensembleMembers.get(query);
			double total = 0;
			for(String name : ensembleMembers.keySet()) {
				Integer ensembleUseCount = ensembleMembers.get(name);
				total += ensembleUseCount;
			}
			total /= ENSEMBLESIZE;
			//			System.out.println("Total Members: " + ensembleMembers.size());
			for(String name : ensembleMembers.keySet()) {
				Integer ensembleUseCount = ensembleMembers.get(name);
				System.out.println("Name: " + name + " Use: " + (ensembleUseCount/total));
			}
			tot += ensembleMembers.size();
		}
		tot /= 16;
		System.out.println("Avg num members users: " + tot);
	}

	public class ModelErrorPair implements Comparable<ModelErrorPair> {

		private String _name;
		private AbstractBidToPrClick _model;
		private double _error;

		public ModelErrorPair(String name, AbstractBidToPrClick model, double error) {
			_name = name;
			_model = model;
			_error = error;
		}

		public String getName() {
			return _name;
		}

		public void setName(String name) {
			_name = name;
		}

		public AbstractBidToPrClick getModel() {
			return _model;
		}

		public void setModel(AbstractBidToPrClick model) {
			_model = model;
		}

		public double getError() {
			return _error;
		}

		public void setError(double error) {
			_error = error;
		}

		public int compareTo(ModelErrorPair modelErrorPair) {
			double thisError = this._error;
			double otherError = modelErrorPair.getError();
			if(thisError < otherError) {
				return -1;
			}
			if(otherError < thisError) {
				return 1;
			}
			else {
				return 0;
			}
		}
	}

}