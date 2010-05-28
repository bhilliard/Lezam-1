package models.queryanalyzer;

import models.queryanalyzer.iep.IEResult;
import models.queryanalyzer.iep.ImpressionEstimator;

import java.util.Arrays;

import models.queryanalyzer.util.LoadData;

import models.queryanalyzer.ds.QAData;
import models.queryanalyzer.ds.QAInstance;

public class TestIEP {
	
	public void run(String[] args) {
		assert(args.length > 0);
		String fileName = args[0];
		System.out.println("Loading File: "+args[0]);
		System.out.println("");
		QAData data = LoadData.LoadIt(fileName);
		
		System.out.println("All Data:");
		System.out.println(data);
		
		int advetiser = 3;
		QAInstance inst = data.buildInstances(advetiser);
		
		System.out.println("Instance for "+advetiser+":");
		System.out.println(inst);
		
		int[] bidOrder = inst.getBidOrder(data);
		System.out.println("Bid order: "+Arrays.toString(bidOrder));
		
		ImpressionEstimator IEP = new ImpressionEstimator(inst);
		IEResult bestSol = IEP.search(bidOrder);
		System.out.println("Best solution: "+Arrays.toString(bestSol.getSol()));
		
		int[] trueImpressions = inst.getTrueImpressions(data);
		System.out.println("Ground Truth:  "+Arrays.toString(trueImpressions));
	}
	
	public static void main(String[] args) throws Exception {
		new TestIEP().run(args);
	}
}