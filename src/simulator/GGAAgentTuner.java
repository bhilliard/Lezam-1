package simulator;

import agents.AbstractAgent;
import agents.rulebased.AdjustPPS;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.ArrayList;

public class GGAAgentTuner {

   private static AbstractAgent _agent;

   public static double evaluateAgent(AbstractAgent agent, String file) {
      BasicSimulator sim = new BasicSimulator();
      double val = 0;
      try {
         val = sim.runSimulations(file, 0, 0, 0, 0, agent);
      } catch (IOException e) {
         e.printStackTrace();
      } catch (ParseException e) {
         e.printStackTrace();
      }
      if (Double.isNaN(val)) {
         return 0.0;
      }

      /*
         * ENSURE GARBAGE COLLECTOR IS RUN BETWEEN ITERATIONS
         */
      System.gc();
      sim.emptyFunction();
      sim.emptyFunction();
      sim.emptyFunction();
      sim.emptyFunction();

      return val;
   }

   public static AbstractAgent createCopy(ArrayList<Double> params) {
      Class<? extends AbstractAgent> c = _agent.getClass();
      Constructor[] constr = c.getConstructors();
      Object[] args = new Object[params.size()];
      for (int i = 0; i < args.length; i++) {
         args[i] = params.get(i);
      }

      AbstractAgent agentCopy = null;

      try {
         agentCopy = (AbstractAgent) (constr[0].newInstance(args));
      } catch (IllegalArgumentException e) {
         e.printStackTrace();
      } catch (InstantiationException e) {
         e.printStackTrace();
      } catch (IllegalAccessException e) {
         e.printStackTrace();
      } catch (InvocationTargetException e) {
         e.printStackTrace();
      }
      return agentCopy;
   }

   public static void main(String[] args) {
      double dummy = 0.0;
      _agent = new AdjustPPS(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
//				_agent = new AdjustPR(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
//				_agent = new AdjustPM(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
//				_agent = new AdjustROI(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
//				_agent = new EquatePPS(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
//				_agent = new EquatePR(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
//				_agent = new EquatePM(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
//				_agent = new EquateROI(dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy, dummy);
      ArrayList<Double> params = new ArrayList<Double>();
      for (int i = 0; i < args.length - 2; i++) {
         params.add(Double.parseDouble(args[i]));
      }
      String baseFile = args[args.length - 2];
      //LAST ARG IS THE SEED WHICH WE IGNORE
      System.out.println((-1.0 * evaluateAgent(createCopy(params), baseFile)));
   }

}
