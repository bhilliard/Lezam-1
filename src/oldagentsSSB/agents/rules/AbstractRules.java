package oldagentsSSB.agents.rules;

import edu.umich.eecs.tac.props.BidBundle;
import edu.umich.eecs.tac.props.Query;

import java.util.Set;

public class AbstractRules {

   protected Set<Query> _querySpace;

   public AbstractRules(Set<Query> _querySpace) {
      this._querySpace = _querySpace;
   }

   public void applyToSet(Set<Query> queries, BidBundle bidBundle) {
      for (Query q : queries) {
         apply(q, bidBundle);
      }
   }

   public void apply(Query q, BidBundle bidBundle) {
      apply(q, bidBundle);
   }

}
