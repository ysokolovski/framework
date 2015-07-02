package org.kevoree.modeling.util.maths;

/**
 * Created by assaad on 02/07/15.
 */
public class GaussianKernel {
    public double[] sum;
    public double[] sumOfSquares;
    public int dim;
    public int total;

    public GaussianKernel(int numOfFeatures){
        dim=numOfFeatures;
        sum=new double[dim];
        sumOfSquares = new double[dim];
        total=0;
    }

    public void addData(double[] features){
        for(int i=0;i<dim;i++){
            sum[i]+=features[i];
            sumOfSquares[i]+=features[i]*features[i];
        }
        total++;
    }

    public void addArrayData(double[][] features){
        for(int i=0;i<features.length;i++){
            for(int j=0; j<dim;j++) {
                sum[j] += features[i][j];
                sumOfSquares[j] += features[i][j] * features[i][j];
            }
        }
        total+=features.length;
    }

    public double[] getMeans(){
        if(total!=0) {
            double[] avg= new double[dim];
            for(int i=0;i<dim;i++){
                avg[i]=sum[i] / total;
            }
            return avg;
        }
        else
            return null;
    }

    public double[] getVariances(){
        if(total!=0) {
            double[] avg= new double[dim];
            double[] newvar= new double[dim];
            for(int i=0;i<dim;i++){
                avg[i]=sum[i] / total;
                newvar[i]=sumOfSquares[i]/total-avg[i]*avg[i];
            }
            return newvar;
        }
        else
            return null;
    }

    public double getProbability(double[] features){
        double[] variances=getVariances();
        double[] means=getMeans();
        double p=1;

        for(int i=0; i<dim; i++){
            p= p* (1/Math.sqrt(2*Math.PI*variances[i]))*Math.exp(-((features[i]-means[i])*(features[i]-means[i]))/(2*variances[i]));
        }
        return p;
    }

    public double[] getParralelProbabilities(double[] features){
        double[] variances=getVariances();
        double[] means=getMeans();
        double[] p=new double[dim];

        for(int i=0; i<dim; i++){
            p[i]=(1/Math.sqrt(2*Math.PI*variances[i]))*Math.exp(-((features[i]-means[i])*(features[i]-means[i]))/(2*variances[i]));
        }
        return p;
    }



}