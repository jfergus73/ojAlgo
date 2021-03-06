/*
 * Copyright 1997-2018 Optimatika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.ann;

import static org.ojalgo.constant.PrimitiveMath.*;
import static org.ojalgo.function.PrimitiveFunction.*;

import java.util.function.UnaryOperator;

import org.ojalgo.access.Access1D;
import org.ojalgo.access.Structure2D;
import org.ojalgo.ann.ArtificialNeuralNetwork.Activator;
import org.ojalgo.function.PrimitiveFunction;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.random.Normal;

final class CalculationLayer implements UnaryOperator<Access1D<Double>> {

    private ArtificialNeuralNetwork.Activator myActivator;
    private final PrimitiveDenseStore myBias;
    private final PrimitiveDenseStore myOutput;
    private final PrimitiveDenseStore myWeights;

    CalculationLayer(int numberOfInputs, int numberOfOutputs, ArtificialNeuralNetwork.Activator activator) {

        super();

        myWeights = PrimitiveDenseStore.FACTORY.makeZero(numberOfInputs, numberOfOutputs);
        myBias = PrimitiveDenseStore.FACTORY.makeZero(1, numberOfOutputs);
        myOutput = PrimitiveDenseStore.FACTORY.makeZero(1, numberOfOutputs);

        myActivator = activator;
    }

    public Access1D<Double> apply(Access1D<Double> input) {
        myWeights.premultiply(input).operateOnMatching(ADD, myBias).supplyTo(myOutput);
        myOutput.modifyAll(myActivator.getFunction(myOutput));
        return myOutput;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof CalculationLayer)) {
            return false;
        }
        CalculationLayer other = (CalculationLayer) obj;
        if (myActivator != other.myActivator) {
            return false;
        }
        if (myBias == null) {
            if (other.myBias != null) {
                return false;
            }
        } else if (!myBias.equals(other.myBias)) {
            return false;
        }
        if (myWeights == null) {
            if (other.myWeights != null) {
                return false;
            }
        } else if (!myWeights.equals(other.myWeights)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((myActivator == null) ? 0 : myActivator.hashCode());
        result = (prime * result) + ((myBias == null) ? 0 : myBias.hashCode());
        result = (prime * result) + ((myWeights == null) ? 0 : myWeights.hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder tmpBuilder = new StringBuilder();
        tmpBuilder.append("Layer [myWeights=");
        tmpBuilder.append(myWeights);
        tmpBuilder.append(", myBias=");
        tmpBuilder.append(myBias);
        tmpBuilder.append(", myActivator=");
        tmpBuilder.append(myActivator);
        tmpBuilder.append(", myOutput=");
        tmpBuilder.append(myOutput);
        tmpBuilder.append("]");
        return tmpBuilder.toString();
    }

    void adjust(final Access1D<Double> layerInput, PrimitiveDenseStore downstreamGradient, double learningRate, PrimitiveDenseStore upstreamGradient) {

        downstreamGradient.modifyMatching(MULTIPLY, myOutput.operateOnAll(myActivator.getDerivativeInTermsOfOutput()));

        if (upstreamGradient != null) {
            // No need to do this multiplication for the input layer
            // input null to stop it...
            myWeights.multiply(downstreamGradient, upstreamGradient);
        }

        for (long j = 0L, numbOutput = myWeights.countColumns(); j < numbOutput; j++) {
            final double grad = downstreamGradient.doubleValue(j);
            for (long i = 0L, numbInput = myWeights.countRows(); i < numbInput; i++) {
                myWeights.add(i, j, learningRate * layerInput.doubleValue(i) * grad);
            }
            myBias.add(j, learningRate * grad);
        }
    }

    double getBias(int output) {
        return myBias.doubleValue(output);
    }

    MatrixStore<Double> getLogicalWeights() {
        return myWeights.logical().below(myBias).get();
    }

    PrimitiveDenseStore getOutput() {
        return myOutput;
    }

    Structure2D getStructure() {
        return myWeights;
    }

    double getWeight(int input, int output) {
        return myWeights.doubleValue(input, output);
    }

    void randomise() {

        double location = ONE / myWeights.countRows();
        double scale = PrimitiveFunction.SQRT.invoke(location);
        Normal generator = new Normal(location, scale);

        myWeights.fillAll(generator);

        myBias.fillAll(generator);
    }

    void setActivator(Activator activator) {
        myActivator = activator;
    }

    void setBias(int output, double bias) {
        myBias.set(output, bias);
    }

    void setWeight(int input, int output, double weight) {
        myWeights.set(input, output, weight);
    }

}
