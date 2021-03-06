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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.mitools.merger.MismatchOnlyPairedReadMerger;
import com.milaboratory.mitools.merger.PairedReadMergingResult;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.reference.Allele;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class VDJCAlignerWithMerge extends VDJCAligner<PairedRead> {
    final VDJCAlignerSJFirst singleAligner;
    final VDJCAlignerPVFirst pairedAligner;
    final MismatchOnlyPairedReadMerger merger;

    public VDJCAlignerWithMerge(VDJCAlignerParameters parameters) {
        super(parameters);
        singleAligner = new VDJCAlignerSJFirst(parameters);
        pairedAligner = new VDJCAlignerPVFirst(parameters);
        merger = new MismatchOnlyPairedReadMerger(parameters.getMergerParameters(),
                parameters.getReadsLayout().isOpposite());
    }

    @Override
    public int addAllele(Allele allele) {
        singleAligner.addAllele(allele);
        pairedAligner.addAllele(allele);
        return super.addAllele(allele);
    }

    @Override
    public void setEventsListener(VDJCAlignerEventListener listener) {
        singleAligner.setEventsListener(listener);
        pairedAligner.setEventsListener(listener);
        super.setEventsListener(listener);
    }

    @Override
    protected void init() {
    }

    @Override
    public VDJCAlignmentResult<PairedRead> process(PairedRead read) {
        PairedReadMergingResult merged = merger.process(read);
        if (merged.isSuccessful()) {
            VDJCAlignments alignment = singleAligner.process(
                    new SingleReadImpl(read.getId(), merged.getOverlappedSequence(), "")).alignment;
            if (listener != null)
                listener.onSuccessfulOverlap(read, alignment);
            return new VDJCAlignmentResult<>(read, alignment);
        } else
            return pairedAligner.process(read);
    }
}
