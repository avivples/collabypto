package document;

import java.io.Serializable;

/**
 * Pair represents a pair of _non-null_ values (x,y).
 */
public class Pair<X, Y> implements Serializable {

    private static final long serialVersionUID = -8784148340029363617L;

    // First object with type X
    public X first;
    // second object with type Y
    public Y second;

    public Pair(X first, Y second) {
       // if (first == null || second == null) throw new NullPointerException();
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Pair<?, ?>)) {
            return false;
        }

        Object otherFirst = ((Pair<?, ?>) o).first;
        Object otherSecond = ((Pair<?, ?>) o).second;
        return this.first.equals(otherFirst) && this.second.equals(otherSecond);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 37 * result + first.hashCode();
        result = 37 * result + second.hashCode();
        return result;
    }


}
