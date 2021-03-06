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

import com.milaboratory.mixcr.reference.Locus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public final class Util {
    private Util() {
    }

    public static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#.##");

    public static Set<Locus> parseLoci(String lociString) {
        String[] split = lociString.split(",");
        EnumSet<Locus> loci = EnumSet.noneOf(Locus.class);
        for (String s : split)
            parseLocus(loci, s);
        return loci;
    }

    private static void parseLocus(Set<Locus> set, String value) {
        switch (value.toLowerCase().trim()) {
            case "tcr":
                set.add(Locus.TRA);
                set.add(Locus.TRB);
                set.add(Locus.TRG);
                set.add(Locus.TRD);
                return;
            case "ig":
                set.add(Locus.IGH);
                set.add(Locus.IGL);
                set.add(Locus.IGK);
                return;
            case "all":
                for (Locus locus : Locus.values())
                    set.add(locus);
                return;
        }
        Locus l = Locus.fromIdSafe(value);
        set.add(l);
        return;
    }

    public static void writeReport(String input, String output,
                                   String commandLineArguments,
                                   String reportFileName,
                                   ReportWriter reportWriter) {
        writeReport(input, output, commandLineArguments, reportFileName, reportWriter, -1);
    }

    public static void writeReport(String input, String output,
                                   String commandLineArguments,
                                   String reportFileName,
                                   ReportWriter reportWriter,
                                   long milliseconds) {
        File file = new File(reportFileName);
        boolean newFile = !file.exists();
        try (FileOutputStream outputStream = new FileOutputStream(file, !newFile)) {
            ReportHelper helper = new ReportHelper(outputStream);
            helper.writeField("Analysis Date", new Date())
                    .writeField("Input file(s)", input)
                    .writeField("Output file", output);

            if (milliseconds != -1)
                helper.writeField("Total timing (ms)", milliseconds);

            if (commandLineArguments != null)
                helper.writeField("Command line arguments", commandLineArguments);

            reportWriter.writeReport(helper);
            helper.end();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }


    public static String printTwoColumns(List<String> left, List<String> right, int leftWidth, int rightWidth, int sep) {
        return printTwoColumns(left, right, leftWidth, rightWidth, sep, "");
    }

    public static String printTwoColumns(List<String> left, List<String> right, int leftWidth, int rightWidth, int sep, String betweenLines) {
        return printTwoColumns(0, left, right, leftWidth, rightWidth, sep, betweenLines);
    }

    public static String printTwoColumns(int offset, List<String> left, List<String> right, int leftWidth, int rightWidth, int sep, String betweenLines) {
        if (left.size() != right.size())
            throw new IllegalArgumentException();
        left = new ArrayList<>(left);
        right = new ArrayList<>(right);
        boolean breakOnNext;
        String spacer = spacer(sep), offsetSpacer = spacer(offset);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < left.size(); ++i) {
            String le = left.get(i), ri = right.get(i);
            breakOnNext = true;
            if (le.length() >= leftWidth && ri.length() >= rightWidth) {
                int leBr = lineBreakPos(le, leftWidth), riBr = lineBreakPos(ri, rightWidth);
                String l = le.substring(0, leBr), r = right.get(i).substring(0, riBr);
                String l1 = le.substring(leBr), r1 = right.get(i).substring(riBr);
                le = l;
                ri = r;
                left.add(i + 1, l1);
                right.add(i + 1, r1);
            } else if (le.length() >= leftWidth) {
                int leBr = lineBreakPos(le, leftWidth);
                String l = le.substring(0, leBr), l1 = le.substring(leBr);
                le = l;
                left.add(i + 1, l1);
                right.add(i + 1, "");
            } else if (ri.length() >= rightWidth) {
                int riBr = lineBreakPos(ri, rightWidth);
                String r = ri.substring(0, riBr), r1 = ri.substring(riBr);
                ri = r;
                right.add(i + 1, r1);
                left.add(i + 1, "");
            } else breakOnNext = false;
            sb.append(offsetSpacer).append(le).append(spacer)
                    .append(spacer(leftWidth - le.length())).append(ri).append('\n');
            if (!breakOnNext)
                sb.append(betweenLines);
        }
        assert left.size() == right.size();
        return sb.toString();
    }

    public static String spacer(int sep) {
        StringBuilder sb = new StringBuilder(sep);
        for (int i = 0; i < sep; ++i)
            sb.append(" ");
        return sb.toString();
    }

    private static int lineBreakPos(String str, int width) {
        int i = width - 1;
        for (; i >= 0; --i)
            if (str.charAt(i) == ' ')
                break;
        if (i <= 3)
            return width - 1;
        return i + 1;
    }
}
