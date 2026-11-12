package diag;

import java.util.ArrayList;
import java.util.List;

/** مرشح واحد في ترتيب «المذنبين المحتملين». */
public final class Candidate implements Comparable<Candidate> {

    public final String id;           // معرّف العرض: مود منسوب أو الحزمة الجذرية
    public final String pkg;          // الحزمة الجذرية (null للمرشحين النصيين فقط)
    public final String attribution;  // mods-list | mixin-config | text | null
    public double score;
    public double sharePct;
    public int rank;
    public int frames;
    public int causedByBlocks;
    public int mixinFrames;
    public boolean topFrame;          // يتصدّر الستاك الرئيسي
    public boolean causedTop;         // يتصدّر إحدى كتل Caused by
    public boolean mixinConfigHit;
    public boolean mixinFailLinked;
    public boolean modsListHit;
    public boolean textHit;
    public final List<String> samples = new ArrayList<>();

    public Candidate(String id, String pkg, String attribution) {
        this.id = id;
        this.pkg = pkg;
        this.attribution = attribution;
    }

    @Override
    public int compareTo(Candidate o) {
        int c = Double.compare(o.score, this.score); // الأعلى أولاً
        if (c != 0) return c;
        c = Integer.compare(o.frames, this.frames);
        if (c != 0) return c;
        return String.CASE_INSENSITIVE_ORDER.compare(this.id, o.id);
    }

    @Override
    public String toString() {
        return id + "(" + score + ")";
    }
}
