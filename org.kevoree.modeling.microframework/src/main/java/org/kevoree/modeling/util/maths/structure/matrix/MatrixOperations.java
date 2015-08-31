package org.kevoree.modeling.util.maths.structure.matrix;

import org.kevoree.modeling.util.maths.structure.KArray2D;
import org.kevoree.modeling.util.maths.structure.blas.KBlas;
import org.kevoree.modeling.util.maths.structure.impl.NativeArray2D;

public class MatrixOperations {



    public static KArray2D transpose(KArray2D matA, KBlas blas) {
        KArray2D result = new NativeArray2D(matA.columns(), matA.rows());
     /*   if (matA.columns() == matA.rows()) {
            transposeSquare(matA, result, blas);
        } else if (matA.columns() > TRANSPOSE_SWITCH && matA.rows() > TRANSPOSE_SWITCH) {
            transposeBlock(matA, result, blas);
        } else {
            transposeStandard(matA, result, blas);
        }*/
        return result;
    }


    public static KArray2D scaleInPlace(KArray2D matA, double alpha, KBlas blas){
        blas.dscal(alpha,matA);
        return matA;
    }

    public static KArray2D multiply(KArray2D matA, KArray2D matB, KBlas blas) {
        NativeArray2D matC = new NativeArray2D(matA.rows(), matB.columns());
        blas.dgemm(blas.NOTRANSPOSE,blas.NOTRANSPOSE,1,matA,matB,0,matC);
        return matC;
    }


    public static KArray2D createIdentity(int width) {
        KArray2D ret = new NativeArray2D(width, width);
        ret.setAll(0);
        for (int i = 0; i < width; i++) {
            ret.set(i, i, 1);
        }
        return ret;
    }

}