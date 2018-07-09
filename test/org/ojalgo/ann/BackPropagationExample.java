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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ojalgo.TestUtils;
import org.ojalgo.access.Access1D;
import org.ojalgo.access.Structure2D;
import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.type.context.NumberContext;

abstract class BackPropagationExample extends ANNTest {

    static final class TrainingTriplet {

        PrimitiveDenseStore expected;
        PrimitiveDenseStore input;
        final double rate;
        PrimitiveDenseStore target;

        TrainingTriplet() {
            this(1.0);
        }

        TrainingTriplet(double rate) {
            super();
            this.rate = rate;
        }

        void expected(double... row) {
            expected = PrimitiveDenseStore.FACTORY.rows(row);
        }

        void input(double... row) {
            input = PrimitiveDenseStore.FACTORY.rows(row);
        }

        void target(double... row) {
            target = PrimitiveDenseStore.FACTORY.rows(row);
        }

    }

    public BackPropagationExample() {
        super();
    }

    @Test
    public void testFeedForward() {

        ArtificialNeuralNetwork network = this.getInitialNetwork().get();

        for (TrainingTriplet triplet : this.getTriplets()) {

            if ((triplet.input != null) && (triplet.expected != null)) {
                TestUtils.assertEquals(triplet.expected, network.apply(triplet.input), this.precision());
            }
        }
    }

    @Test
    public void testTraining() {
        for (TrainingTriplet triplet : this.getTriplets()) {
            if ((triplet.input != null) && (triplet.target != null)) {
                this.deriveTheHardWay(this.getInitialNetwork(), triplet, this.precision());
            }
        }
    }

    protected void deriveTheHardWay(NetworkBuilder builder, TrainingTriplet triplet, NumberContext precision) {

        double delta = 0.0001;

        Structure2D[] structure = builder.getStructure();

        PrimitiveDenseStore[] weights = new PrimitiveDenseStore[structure.length];
        PrimitiveDenseStore[] bias = new PrimitiveDenseStore[structure.length];

        for (int layer = 0, limit = structure.length; layer < limit; layer++) {

            PrimitiveDenseStore newWeights = weights[layer] = PrimitiveDenseStore.FACTORY.makeZero(structure[layer]);
            PrimitiveDenseStore newBias = bias[layer] = PrimitiveDenseStore.FACTORY.makeZero(1, structure[layer].countColumns());

            for (int output = 0; output < structure[layer].countColumns(); output++) {
                for (int input = 0; input < structure[layer].countRows(); input++) {

                    double orgWeight = builder.getWeight(layer, input, output);

                    builder.weight(layer, input, output, orgWeight + delta);
                    double upperError = builder.error(triplet.target, builder.get().apply(triplet.input));
                    builder.weight(layer, input, output, orgWeight - delta);
                    double lowerError = builder.error(triplet.target, builder.get().apply(triplet.input));
                    builder.weight(layer, input, output, orgWeight);

                    final double derivative = (upperError - lowerError) / (delta + delta);
                    newWeights.set(input, output, orgWeight - (triplet.rate * derivative));
                }

                double orgBias = builder.getBias(layer, output);

                builder.bias(layer, output, orgBias + delta);
                double upperError = builder.error(triplet.target, builder.get().apply(triplet.input));
                builder.bias(layer, output, orgBias - delta);
                double lowerError = builder.error(triplet.target, builder.get().apply(triplet.input));
                builder.bias(layer, output, orgBias);

                final double derivative = (upperError - lowerError) / (delta + delta);
                newBias.set(output, orgBias - (triplet.rate * derivative));
            }

        }

        builder.train(triplet.input, triplet.target, triplet.rate);

        for (int layer = 0, limit = structure.length; layer < limit; layer++) {
            for (int output = 0; output < structure[layer].countColumns(); output++) {
                for (int input = 0; input < structure[layer].countRows(); input++) {
                    final PrimitiveDenseStore expectedWeights = weights[layer];
                    TestUtils.assertEquals(expectedWeights.doubleValue(input, output), builder.getWeight(layer, input, output), precision);
                }
                TestUtils.assertEquals(bias[layer].doubleValue(output), builder.getBias(layer, output), precision);
            }
        }

    }

    protected abstract NetworkBuilder getInitialNetwork();

    protected abstract List<TrainingTriplet> getTriplets();

    protected abstract NumberContext precision();

    void compare(Access1D<Double> input, ArtificialNeuralNetwork network, NumberContext precision, Access1D<Double>... layerOutput) {

        Access1D<Double> output = network.apply(input);
        TestUtils.assertEquals(output, network.getOutput(layerOutput.length - 1), precision);

        for (int l = 0; l < layerOutput.length; l++) {
            TestUtils.assertEquals(layerOutput[l], network.getOutput(l), precision);
        }

    }

}
