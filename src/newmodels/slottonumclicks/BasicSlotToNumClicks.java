package newmodels.slottonumclicks;

/**
 * @author jberg
 *
 */

import newmodels.querytonumimp.AbstractQueryToNumImp;
import newmodels.slottoprclick.AbstractSlotToPrClick;
import edu.umich.eecs.tac.props.Query;
import edu.umich.eecs.tac.props.QueryReport;
import edu.umich.eecs.tac.props.SalesReport;

public class BasicSlotToNumClicks extends AbstractSlotToNumClicks {

	private AbstractQueryToNumImp _queryToNumImp;
	private AbstractSlotToPrClick _slotToPrClick;

	public BasicSlotToNumClicks(Query query,
								AbstractSlotToPrClick slotToPrClick,
								AbstractQueryToNumImp queryToNumImp) {
		super(query);
		_slotToPrClick = slotToPrClick;
		_queryToNumImp = queryToNumImp;
	}

	@Override
	public int getPrediction(double slot) {
		double clickPr = _slotToPrClick.getPrediction(slot);
		int numImp = _queryToNumImp.getPrediction(_query);
		return (int) (clickPr * numImp);
	}

	@Override
	public boolean updateModel(QueryReport queryReport, SalesReport salesReport) {
		//Nothing to do
		return true;
	}

}
