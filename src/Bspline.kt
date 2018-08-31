package geoModel

import linearAlgebra.Vector3

open class Bspline: Parametric {

    /*  A B-Spline curve is defined by
        C(oldVector) = Sum( Ni(oldVector) * Pi )
        where Pi are the control points, and Ni(oldVector) are the basis funcs defined on the knot vector */
    var maxDeg = 0
    
    final override var prm = mutableListOf<Double>()
    final override var ctrlPts = mutableListOf<Vector3>()
    
    protected val knots = mutableListOf<Double>()
    protected var degree = 0
    protected var order = 1

    constructor(max: Int) {
        maxDeg = max
    }
    
    constructor(max: Int, p: MutableList<Vector3>) {
        this.ctrlPts = p
        evalPrm(ctrlPts); degree(); order(); evalKnots()
    }
    
    protected fun degree() {
        val nm1 = prm.size - 1
        degree = when(nm1 > maxDeg) {
            true  -> maxDeg
            false -> nm1
        }
    }

    protected fun order() { order = degree + 1}

    override fun addPts(v: Vector3) {
        ctrlPts.add(v)
        evalPrm(ctrlPts); degree(); order();  evalKnots()
    }

    override fun addPts(i: Int, v: Vector3) {
        ctrlPts.add(i, v)
        evalPrm(ctrlPts); degree(); order();  evalKnots()
    }

    override fun removePts(i: Int) {
        if(i != -1) ctrlPts.removeAt(i)
        if(!ctrlPts.isEmpty()) { evalPrm(ctrlPts); degree(); order();  evalKnots() }
    }

    final override fun evalPrm(p: MutableList<Vector3>) {
        prm.clear()
        var sum = 0.toDouble()
        prm.add(sum)
        //Chord length method
        for(i in 1 until p.count())
        {
            val del = p[i] - p[i - 1]
            sum += del.length
        }
        for(i in 1 until p.count())
        {
            val del = p[i] - p[i - 1]
            prm.add(prm[i - 1] + del.length / sum)
        }
    }

    protected open fun evalKnots() {
        /*  Evaluate knot vector. For the uniform spacing, e.g.
        ctrlPts 1 (point)    : n=1,deg=0,order=1,knots={0,1}
        ctrlPts 2 (linear)   : n=2,deg=1,order=2,knots={0,0,1,1}
        ctrlPts 3 (quadratic): n=3,deg=2,order=3,knots={0,0,0,1,1,1}
        ctrlPts 4 (cubic)    : n=4,deg=3,order=4,knots={0,0,0,0,1,1,1,1}
        ctrlPts 5 (quartic)  : n=5,deg=4,order=5,knots={0,0,0,0,0,1,1,1,1,1}
        ctrlPts 6 (quintic)  : n=6,deg=5,order=6,knots={0,0,0,0,0,0,1,1,1,1,1,1}
        ctrlPts 7 (quintic)  : n=7,deg=5,order=6,knots={0,0,0,0,0,0,1/2,1,1,1,1,1,1}
        ctrlPts 8 (quintic)  : n=8,deg=5,order=6,knots={0,0,0,0,0,0,1/3,2/3,1,1,1,1,1,1}
        general   (quintic)  : n= ,deg=5,order=6,knots={...,[1/(n-deg),...,(n-order)/(n-deg)],...,} */
        knots.clear()
        for(i in 1..order) knots.add(0.toDouble())
        for(i in 1..order) knots.add(1.toDouble())
        for(i in 1..ctrlPts.size - order) {
            var interval = 0.0
            //averaging spacing(reflecting the distribution of prm)
            for(j in i until i + degree) interval += prm[j]
            interval /= degree
            knots.add(degree + i, interval)
        }
    }

    //Algorithm 2.1
    protected fun findIndexSpan(n: Int, t: Double): Int {
        var t = t
        //Make sure the parameter t is within the knots range
        if(t > knots.max()?: 1.0) t = 1.0
        if(t < knots.min()?: 0.0) t = 0.0
        //Find index of ith knot span(half-open interval)
        val nm1 = n - 1
        if(t >= knots.max()?: 1.0) return nm1 //special case of t at the curve end
        var low: Int  = degree
        var high: Int = nm1 + 1
        var mid: Int  = (high + low) / 2
        // Do binary search
        while(t < knots[mid] || t >= knots[mid + 1]) {
            if(t < knots[mid])
                high = mid
            else
                low = mid
            mid = (high + low) / 2
            //println("stuck in while loop: t = $t, mid=${knots[mid]}, mid+1=${knots[mid+1]}")
        }
        return mid
    }

    //Algorithm 2.2
    protected fun basisFuncs(span: Int, t: Double) : DoubleArray {
        /*  Compute nonvanishing basis functions
        deg = 0 : Ni are step func.
        deg = 1 : linear basis func.
        deg = 2 : quadratic basis func.
        deg = 3 : cubic basis func.
        deg = 4 : quartic  basis func.
        deg = 5 : quintic basis func.   */
        val left = DoubleArray(order)
        val right = DoubleArray(order)
        val ni = DoubleArray(order)
        ni[0] = 1.0
        for(j in 1..degree) {
            left[j] = t - knots[span + 1 - j]
            right[j] = knots[span + j] - t
            var saved = 0.0
            for(k in 0 until j) {
                val tmp = ni[k] / (right[k + 1] + left[j - k])
                ni[k] = saved + right[k + 1] * tmp
                saved = left[j - k] * tmp
            }
            ni[j] = saved
        }
        return ni
    }

    //Algorithm 2.3
    protected fun dersBasisFunc(span: Int, t: Double, kmax: Int): Array<DoubleArray> {
        /* Compute nonzero basis functions and their derivatives
        ders[k][j] is the kth derivative where 0 <= k <= kmax and 0 <= j <= degree
        First section is A.2.2 modified to store functions and knot differnces.  */
        val ders = Array(kmax + 1, {DoubleArray(order)})
        var d: Double
        val left  = DoubleArray(order)
        val right = DoubleArray(order)
        // basis functions and knot differences
        val ndu = Array(order, {DoubleArray(order)})
        // two most recently computed rows a(k,j) and a(k-1,j)
        val a = Array(2, {DoubleArray(order)})
        var tmp  : Double
        var saved: Double
        ndu[0][0] = 1.0
        for(j in 1..degree)
        {
            left[j] = t - knots[span + 1 - j]
            right[j] = knots[span + j] - t
            saved = 0.0
            for (r in 0 until j)
            {
                // Lower triangle
                ndu[j][r] = right[r + 1] + left[j - r]
                tmp = ndu[r][j - 1] / ndu[j][r]
                // Upper triangle
                ndu[r][j] = saved + right[r + 1] * tmp
                saved = left[j - r] * tmp
            }
            ndu[j][j] = saved;
        }
        // Load the basis functions
        for (j in 0..degree)
        {
            ders[0][j] = ndu[j][degree]
        }
        // This section computes the derivatives (Eq. [2.9])
        for (r in 0..degree) //Loop over function index
        {
            var s1 = 0; var s2 = 1 // Alternative rows in array a
            a[0][0] = 1.0
            // Loop to compute kth derivative
            for (k in 1..kmax)
            {
                d = 0.0
                var rk = r - k
                val pk = degree - k
                if (r >= k)
                {
                    a[s2][0] = a[s1][0] / ndu[pk + 1][rk]
                    d = a[s2][0] * ndu[rk][pk]
                }
                val j1 = when(rk >= -1) {
                    true  -> 1
                    false -> -rk
                }
                val j2 = when(r - 1 <= pk) {
                    true  -> k - 1
                    false -> degree - r
                }
                for (j in j1..j2)
                {
                    a[s2][j] = (a[s1][j] - a[s1][j - 1]) / ndu[pk + 1][rk + j]
                    d += a[s2][j] * ndu[rk + j][pk]
                }
                if (r <= pk)
                {
                    a[s2][k] = -a[s1][k - 1] / ndu[pk + 1][r]
                    d += a[s2][k] * ndu[r][pk]
                }
                ders[k][r] = d
                rk = s1; s1 = s2; s2 = rk //Switch rows
            }
        }
        // Multiply through by the correct factors (Eq. [2.9])
        var cf = degree
        for (k in 1..kmax)
        {
            for (j in 0..degree) ders[k][j] *= cf.toDouble()
            cf *= (degree - k)
        }
        return ders
    }

    //Algorithm 3.1
    override fun curvePoint(t: Double): Vector3 {
        val span = findIndexSpan(ctrlPts.size, t)
        val nn = basisFuncs(span, t)
        var v = Vector3().zero
        for (j in 0..degree)
        {
            v += ctrlPts[span - degree + j] * nn[j]
        }
        return v
    }

    //Algorithm 3.2
    override fun curveDers(kmax: Int, t: Double): Array<Vector3> {
        // Compute kth derivatives
        val v = Array(kmax + 1) { Vector3() }
        /* Allow kmax > degree, although the ders. are 0 in this case for nonrational curves,
            but these ders. are needed for rational curves */
        val du = minOf(kmax, degree)
        for(k in order..kmax) v[k] = Vector3().zero
        val span = findIndexSpan(ctrlPts.size, t)
        val nders = dersBasisFunc(span, t, du)
        for (k in 0..du)
        {
            v[k] = Vector3().zero
            for (j in 0..degree)
            {
                v[k] += ctrlPts[span - degree + j] * nders[k][j]
            }
        }
        return v
    }

    //Algorithm 5.1
    private fun curveKnotInsert(t: Double)
    {
        val low: Int
        val q = mutableListOf<Vector3>()
        val span = findIndexSpan(ctrlPts.size, t)

        for (i in 0..degree)
        {
            val tmp: Vector3 = ctrlPts[span - degree + i]
            q.add(tmp)
        }
        low = span - degree + 1
        for (i in 0 until degree)
        {
            val alpha = (t - knots[low + i]) / (knots[span + 1 + i] - knots[low + i])
            q[i] =  q[i + 1] * alpha + q[i] * (1.0f - alpha)
        }
        for (i in 0 until degree)
        {
            if (i != degree - 1)
            {
                ctrlPts.removeAt(low + i)
            }
            ctrlPts.add(low + i, q[i])
        }
        knots.add(span + 1, t)
        /*
        super.addPts(Vector3().zero) //dummy corresponding control points

        evalPrmKnotAverages()
        for (i in 1 until pts.size - 1)
        {
            //Replaces pts at the specified position in this list
            pts[i] = curvePoint(prm[i])
        }
        */
    }

    /*
    private fun evalPrmKnotAverages()
    {
        for (i in pts.indices)
        {
            var average = 0.0
            for (j in 1..degree)
            {
                average += knots[i + j]
            }
            average /= degree
            prm[i] = average
        }
    }
    */

    fun closestPoint(v: Vector3): Vector3 {
        //initial search
        val n = 8 * ctrlPts.size
        var t = 0.0
        var min = (curvePoint(t) - v).length
        for(i in 1..n) {
            val tmp = i.toDouble() / n
            val dum = (curvePoint(tmp) - v).length
            if(dum < min) {
                min = dum
                t = tmp
            }
        }
        var u = curveDers(2, t)
        var isOrthogonal = false
        var isCoincidence = false
        while(!isOrthogonal || !isCoincidence) {
            t -= u[1].dot(u[0] - v) / (u[2].dot(u[0] - v) + u[1].dot(u[1]))
            u = curveDers(2, t)
            isOrthogonal = u[1].dot(u[0] - v) < 0.000001
            isCoincidence = (u[0] - v).length < 0.000001
        }
        TODO()
    }
}
