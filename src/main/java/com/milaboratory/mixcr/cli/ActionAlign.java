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

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Chunk;
import cc.redberry.pipe.util.CountLimitingOutputPort;
import cc.redberry.pipe.util.Indexer;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReaderCloseable;
import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.mitools.cli.Action;
import com.milaboratory.mitools.cli.ActionHelper;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.reference.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;

import java.io.IOException;
import java.util.*;

import static cc.redberry.pipe.CUtils.chunked;
import static cc.redberry.pipe.CUtils.unchunked;

public class ActionAlign implements Action {
    private final AlignParameters actionParameters = new AlignParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        VDJCAlignerParameters alignerParameters = actionParameters.getAlignerParameters();

        if (!actionParameters.overrides.isEmpty()) {
            alignerParameters = JsonOverrider.override(alignerParameters, VDJCAlignerParameters.class, actionParameters.overrides);
            if (alignerParameters == null)
                System.err.println("Failed to override some parameter.");
        }

        VDJCAligner aligner = VDJCAligner.createAligner(alignerParameters,
                actionParameters.isInputPaired(), !actionParameters.noMerge);

        LociLibrary ll = LociLibraryManager.getDefault().getLibrary("mi");

        for (Locus locus : actionParameters.getLoci())
            for (Allele allele : ll.getLocus(actionParameters.getTaxonID(), locus).getAllAlleles())
                if (alignerParameters.containsRequiredFeature(allele) &&
                        (allele.isFunctional() || !actionParameters.isFunctionalOnly()))
                    aligner.addAllele(allele);
//                else if (allele.isFunctional())
//                    System.err.println("WARNING: Functional allele excluded " + allele.getName());

        AlignerReport report = actionParameters.report == null ? null : new AlignerReport();
        if (report != null)
            aligner.setEventsListener(report);

        try (SequenceReaderCloseable<? extends SequenceRead> reader = actionParameters.createReader();
             VDJCAlignmentsWriter writer = actionParameters.getOutputName().equals(".") ? null : new VDJCAlignmentsWriter(actionParameters.getOutputName())) {
            if (writer != null) writer.header(aligner);
            SmartProgressReporter.startProgressReport("Alignment", (CanReportProgress) reader);
            OutputPort<? extends SequenceRead> sReads = reader;
            if (actionParameters.limit != 0)
                sReads = new CountLimitingOutputPort<>(sReads, actionParameters.limit);
            OutputPort<Chunk<? extends SequenceRead>> mainInputReads = CUtils.buffered((OutputPort) chunked(sReads, 64), 16);
            OutputPort<VDJCAlignmentResult> alignments = unchunked(new ParallelProcessor(mainInputReads, chunked(aligner), actionParameters.threads));
            for (VDJCAlignmentResult result : CUtils.it(
                    new OrderedOutputPort<>(alignments,
                            new Indexer<VDJCAlignmentResult>() {
                                @Override
                                public long getIndex(VDJCAlignmentResult o) {
                                    return o.read.getId();
                                }
                            }))) {
                if (result.alignment == null)
                    continue;
                if (writer != null)
                    writer.write(result.alignment);
            }
            if (writer != null)
                writer.setNumberOfProcessedReads(reader.getNumberOfReads());
        }

        if (report != null)
            Util.writeReport(actionParameters.getInputForReport(), actionParameters.getOutputName(),
                    helper.getCommandLineArguments(), actionParameters.report, report);
    }

    @Override
    public String command() {
        return "align";
    }

    @Override
    public AlignParameters params() {
        return actionParameters;
    }

    @Parameters(commandDescription = "Builds alignments with V,D,J and C genes for input sequencing reads.",
            optionPrefixes = "-")
    public static class AlignParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file1 [input_file2] output_file.vdjca", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @DynamicParameter(names = "-O", description = "Overrides base values of parameters.")
        private Map<String, String> overrides = new HashMap<>();

        @Parameter(description = "Parameters",
                names = {"-p", "--parameters"})
        public String alignerParametersName = "default";

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "Species (organism). Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, etc..",
                names = {"-s", "--species"})
        public String species = "HomoSapiens";

        @Parameter(description = "Immunological loci to align with separated by ','. Available loci: IGH, IGL, IGK, TRA, TRB, TRG, TRD.",
                names = {"-l", "--loci"})
        public String loci = "all";

        @Parameter(description = "Processing threads",
                names = {"-t", "--threads"}, validateWith = PositiveInteger.class)
        public int threads = Runtime.getRuntime().availableProcessors();

        @Parameter(description = "Maximal number of reads to process",
                names = {"-n", "--limit"}, validateWith = PositiveInteger.class)
        public long limit = 0;

        @Parameter(description = "Use only functional alleles.",
                names = {"-u", "--functional"})
        public Boolean functionalOnly = null;

        @Parameter(description = "Do not merge paired reads.",
                names = {"-d", "--noMerge"})
        public Boolean noMerge = false;

        public int getTaxonID() {
            return Species.fromStringStrict(species);
        }

        public VDJCAlignerParameters getAlignerParameters() {
            VDJCAlignerParameters params = VDJCParametersPresets.getByName(alignerParametersName);
            if (params == null)
                throw new ParameterException("Unknown aligner parameters: " + alignerParametersName);
            return params;
        }

        public boolean isFunctionalOnly() {
            return functionalOnly != null && functionalOnly;
        }

        public String getInputForReport() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; ; ++i) {
                builder.append(parameters.get(i));
                if (i == parameters.size() - 2)
                    break;
                builder.append(',');
            }
            return builder.toString();
        }

        public Set<Locus> getLoci() {
            return Util.parseLoci(loci);
        }

        public boolean isInputPaired() {
            return parameters.size() == 3;
        }

        public String getOutputName() {
            return parameters.get(parameters.size() - 1);
        }

        public SequenceReaderCloseable<? extends SequenceRead> createReader() throws IOException {
            if (isInputPaired())
                return new PairedFastqReader(parameters.get(0), parameters.get(1));
            else {
                String[] s = parameters.get(0).split("\\.");
                if (s[s.length - 1].equals("fasta"))
                    return new FastaReader(parameters.get(0), true);
                else
                    return new SingleFastqReader(parameters.get(0));
            }
        }

        @Override
        protected List<String> getOutputFiles() {
            return Arrays.asList(getOutputName());
        }

        @Override
        public void validate() {
            if (parameters.size() > 3)
                throw new ParameterException("Too many input files.");
            if (parameters.size() < 2)
                throw new ParameterException("No output file.");
            super.validate();
        }
    }
}