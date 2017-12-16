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
public class EpanechnikovKernel extends Kernel{
    private float k;

    public EpanechnikovKernel(float k) {
        this.k = k;
    }

    @Override
    float eval(float x) {
        return Math.abs(x /= k) <= 1 ? 0.75f * (1 - x * x) / k : 0;
    }
}
