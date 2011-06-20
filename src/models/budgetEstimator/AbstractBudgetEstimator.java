package models.budgetEstimator;

import edu.umich.eecs.tac.props.*;
import models.AbstractModel;
import simulator.parser.GameStatusHandler;

import java.util.HashMap;

public abstract class AbstractBudgetEstimator extends AbstractModel {

   public abstract double getBudgetEstimate(Query q, String advertiser);

   public abstract void updateModel(QueryReport queryReport,
                                    BidBundle bidBundle,
                                    double[] convProbs,
                                    HashMap<Query, Double> contProbs,
                                    double[] regReserves,
                                    HashMap<Query, int[]> allOrders,
                                    HashMap<Query, int[]> allImps,
                                    HashMap<Query, int[][]> waterfalls,
                                    HashMap<Query, double[]> bids,
                                    HashMap<Product, HashMap<GameStatusHandler.UserState, Integer>> userStates);

}
