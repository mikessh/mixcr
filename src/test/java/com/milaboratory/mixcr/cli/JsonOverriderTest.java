/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.BandedAlignerParameters;
import com.milaboratory.core.alignment.KAlignerParameters;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.vdjaligners.DAlignerParameters;
import org.junit.Assert;
import org.junit.Test;

public class JsonOverriderTest {
    @Test
    public void test1() throws Exception {
        KAlignerParameters parameters = KAlignerParameters.getByName("default");
        KAlignerParameters override = JsonOverrider.override(
                parameters,
                KAlignerParameters.class,
                "floatingLeftBound=true",
                "scoring.subsMatrix=simple(match=4,mismatch=-9)");
        KAlignerParameters expected = parameters.clone().setFloatingLeftBound(true)
                .setScoring(new LinearGapAlignmentScoring(NucleotideSequence.ALPHABET, 4, -9, parameters.getScoring().getGapPenalty()));
        Assert.assertEquals(expected, override);
    }

    @Test
    public void test2() throws Exception {
        KAlignerParameters parameters = KAlignerParameters.getByName("default");
        KAlignerParameters override = JsonOverrider.override(
                parameters,
                KAlignerParameters.class,
                "floatingLeftBound=true",
                "subsMatrix=simple(match=4,mismatch=-9)");
        KAlignerParameters expected = parameters.clone().setFloatingLeftBound(true)
                .setScoring(new LinearGapAlignmentScoring(NucleotideSequence.ALPHABET, 4, -9, parameters.getScoring().getGapPenalty()));
        Assert.assertEquals(expected, override);
    }

    @Test
    public void testArray1() throws Exception {
        CloneFactoryParameters factoryParameters = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(GeneFeature.VRegion, 0.3f,
                        new BandedAlignerParameters(LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3, -50)),
                new VJCClonalAlignerParameters(GeneFeature.JRegion, 0.4f,
                        new BandedAlignerParameters(LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5, -40)),
                null, new DAlignerParameters(GeneFeature.DRegion, 30.0f, 0.85f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );

        CloneAssemblerParameters params = new CloneAssemblerParameters(new GeneFeature[]{GeneFeature.FR1, GeneFeature.CDR3},
                new CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, (byte) 20, .8, "2of6");

        CloneAssemblerParameters override = JsonOverrider.override(
                params,
                CloneAssemblerParameters.class,
                "assemblingFeatures=[CDR1(-5,+6),CDR2]");

        CloneAssemblerParameters expected = new CloneAssemblerParameters(new GeneFeature[]{new GeneFeature(GeneFeature.CDR1, -5, +6), GeneFeature.CDR2},
                new CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, (byte) 20, .8, "2of6");


        Assert.assertEquals(expected, override);
    }
}