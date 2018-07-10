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

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.ojalgo.access.Access1D;
import org.ojalgo.access.Structure2D;
import org.ojalgo.function.PrimitiveFunction;
import org.ojalgo.function.aggregator.Aggregator;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PrimitiveDenseStore;

public final class ArtificialNeuralNetwork implements UnaryOperator<Access1D<Double>> {

    public static enum Activator {

        /**
         * (-,+)
         */
        IDENTITY(args -> (arg -> arg), arg -> ONE),
        /**
         * ReLU: [0,+)
         */
        RECTIFIER(args -> (arg -> Math.max(ZERO, arg)), arg -> arg > ZERO ? ONE : ZERO),
        /**
         * [0,1]
         */
        SIGMOID(args -> (PrimitiveFunction.LOGISTIC), arg -> arg * (ONE - arg)),
        /**
         * [0,1]
         */
        SOFTMAX(args -> {
            PrimitiveDenseStore parts = args.copy();
            parts.modifyAll(PrimitiveFunction.EXP);
            final double total = parts.aggregateAll(Aggregator.SUM);
            return arg -> PrimitiveFunction.EXP.invoke(arg) / total;
        }, arg -> ONE),
        /**
         * [-1,1]
         */
        TANH(args -> (PrimitiveFunction.TANH), arg -> ONE - (arg * arg));

        private final PrimitiveFunction.Unary myDerivativeInTermsOfOutput;
        private final ActivatorFunctionFactory myFunction;

        Activator(ActivatorFunctionFactory function, PrimitiveFunction.Unary derivativeInTermsOfOutput) {
            myFunction = function;
            myDerivativeInTermsOfOutput = derivativeInTermsOfOutput;
        }

        //        UnaryFunction<Double> getDerivative() {
        //            return myFunction.andThen(myDerivativeInTermsOfOutput);
        //        }

        PrimitiveFunction.Unary getDerivativeInTermsOfOutput() {
            return myDerivativeInTermsOfOutput;
        }

        PrimitiveFunction.Unary getFunction(PrimitiveDenseStore arguments) {
            return myFunction.make(arguments);
        }
    }

    public static enum Error implements PrimitiveFunction.Binary {

        /**
         *
         */
        CROSS_ENTROPY((target, current) -> -target * Math.log(current), (target, current) -> (current - target)),
        /**
         *
         */
        HALF_SQUARED_DIFFERENCE((target, current) -> HALF * (target - current) * (target - current), (target, current) -> (current - target));

        private final PrimitiveFunction.Binary myDerivative;
        private final PrimitiveFunction.Binary myFunction;

        Error(PrimitiveFunction.Binary function, PrimitiveFunction.Binary derivative) {
            myFunction = function;
            myDerivative = derivative;
        }

        public double invoke(Access1D<?> target, Access1D<?> current) {
            int limit = (int) Math.min(target.count(), current.count());
            double retVal = ZERO;
            for (int i = 0; i < limit; i++) {
                retVal += myFunction.invoke(target.doubleValue(i), current.doubleValue(i));
            }
            return retVal;
        }

        public double invoke(double target, double current) {
            return myFunction.invoke(target, current);
        }

        PrimitiveFunction.Binary getDerivative() {
            return myDerivative;
        }

    }

    static interface ActivatorFunctionFactory {

        PrimitiveFunction.Unary make(PrimitiveDenseStore arguments);

    }

    private final Layer[] myLayers;

    ArtificialNeuralNetwork(int inputs, int[] layers) {
        super();
        myLayers = new Layer[layers.length];
        int tmpIn = inputs;
        int tmpOut = inputs;
        for (int i = 0; i < layers.length; i++) {
            tmpIn = tmpOut;
            tmpOut = layers[i];
            myLayers[i] = new Layer(tmpIn, tmpOut, Activator.SIGMOID);
        }
    }

    public Access1D<Double> apply(Access1D<Double> input) {
        Access1D<Double> retVal = input;
        for (int i = 0, limit = myLayers.length; i < limit; i++) {
            retVal = myLayers[i].apply(retVal);
        }
        return retVal;
    }

    void backpropagate(Access1D<Double> input, PrimitiveDenseStore downstreamGradient, double learningRate) {
        for (int k = myLayers.length - 1; k >= 0; k--) {
            myLayers[k].adjust(k == 0 ? input : myLayers[k - 1].getOutput(), downstreamGradient, learningRate);
        }
    }

    int countCalculationLayers() {
        return myLayers.length;
    }

    double getBias(int layer, int output) {
        return myLayers[layer].getBias(output);
    }

    Access1D<Double> getOutput(int layer) {
        return myLayers[layer].getOutput();
    }

    Structure2D[] getStructure() {

        Structure2D[] retVal = new Structure2D[myLayers.length];

        for (int l = 0; l < retVal.length; l++) {
            retVal[l] = myLayers[l].getStructure();
        }

        return retVal;
    }

    double getWeight(int layer, int input, int output) {
        return myLayers[layer].getWeight(input, output);
    }

    List<MatrixStore<Double>> getWeights() {
        final ArrayList<MatrixStore<Double>> retVal = new ArrayList<>();
        for (int i = 0; i < myLayers.length; i++) {
            retVal.add(myLayers[i].getWeights());
        }
        return retVal;
    }

    void randomise() {
        for (int i = 0, limit = myLayers.length; i < limit; i++) {
            myLayers[i].randomise();
        }
    }

    void setActivator(int layer, Activator activator) {
        myLayers[layer].setActivator(activator);
    }

    void setBias(int layer, int output, double bias) {
        myLayers[layer].setBias(output, bias);
    }

    void setWeight(int layer, int input, int output, double weight) {
        myLayers[layer].setWeight(input, output, weight);
    }

}
