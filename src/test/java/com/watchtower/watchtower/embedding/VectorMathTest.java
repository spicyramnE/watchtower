package com.watchtower.watchtower.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class VectorMathTest {

    @Test
    void cosineSimilarity_ofIdenticalVectors_isOne() {
        double[] a = {1, 2, 3};
        double[] b = {1, 2, 3};

        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_ofOrthogonalVectors_isZero() {
        double[] a = {1, 0};
        double[] b = {0, 1};

        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_ofOppositeVectors_isNegativeOne() {
        double[] a = {1, 0};
        double[] b = {-1, 0};

        assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_isScaleInvariant() {
        double[] a = {1, 2, 3};
        double[] scaled = {10, 20, 30};

        assertThat(VectorMath.cosineSimilarity(a, scaled)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cosineSimilarity_withZeroVector_isZeroRatherThanNaN() {
        double[] zero = {0, 0, 0};
        double[] other = {1, 2, 3};

        assertThat(VectorMath.cosineSimilarity(zero, other)).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarity_withMismatchedLengths_throws() {
        double[] a = {1, 2};
        double[] b = {1, 2, 3};

        assertThatThrownBy(() -> VectorMath.cosineSimilarity(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
