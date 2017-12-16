/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.grid;

/**
 *
 * @author Márcio Peres
 */
public class GaussianKernel extends Kernel{

    final double invSqrt2xPI = 1/Math.sqrt(2 * Math.PI);
    
    @Override
    float eval(float x) {
        return (float)Math.exp(-0.5f*x*x) * (float)invSqrt2xPI;
    }
}
