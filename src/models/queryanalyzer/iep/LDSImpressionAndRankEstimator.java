package models.queryanalyzer.iep;

import models.queryanalyzer.ds.QAInstance;
import models.queryanalyzer.search.LDSearchIESmart;

public class LDSImpressionAndRankEstimator implements ImpressionAndRankEstimator {

   public final static int NUM_SLOTS = 5;
   public int NUM_ITERATIONS_2 = 20;
   private QAInstance inst;
   private AbstractImpressionEstimator ie;

   public LDSImpressionAndRankEstimator(AbstractImpressionEstimator ie) {
      this.ie = ie;
      this.inst = ie.getInstance();
   }

   public IEResult getBestSolution() {
      double[] avgPos = ie.getApproximateAveragePositions();
//      int[] avgPosOrder = inst.getAvgPosOrder(avgPos);
		int[] avgPosOrder = inst.getCarletonOrder(avgPos, NUM_SLOTS);
      IEResult bestSol;
      int numActualAgents = inst.getNumAdvetisers(); //regardless of any padding

      //System.out.println("avgPos=" + Arrays.toString(avgPos) + ", avgPosOrder=" + Arrays.toString(avgPosOrder));
      if(inst.getImpressions() > 0) {
         if(avgPosOrder.length > 0) {
            LDSearchIESmart smartIESearcher = new LDSearchIESmart(NUM_ITERATIONS_2, ie);
            smartIESearcher.search(avgPosOrder, avgPos);
            //LDSearchHybrid smartIESearcher = new LDSearchHybrid(NUM_ITERATIONS_1, NUM_ITERATIONS_2, inst);
            //smartIESearcher.search();
            bestSol = smartIESearcher.getBestSolution();
            if(bestSol == null || bestSol.getSol() == null) {
               int[] imps = new int[numActualAgents];
               int[] slotimps = new int[NUM_SLOTS];
               bestSol = new IEResult(0, imps, avgPosOrder, slotimps); //FIXME: What to do about avgPosOrder? This could contain padded agents as it stands
            }
         }
         else {
            int[] imps = new int[numActualAgents];
            int[] slotimps = new int[NUM_SLOTS];
            bestSol = new IEResult(0, imps, avgPosOrder, slotimps);
         }
      }
      else {
         int[] imps = new int[numActualAgents];
         int[] slotimps = new int[NUM_SLOTS];
         bestSol = new IEResult(0, imps, avgPosOrder, slotimps);
      }


      return bestSol;
   }

}
