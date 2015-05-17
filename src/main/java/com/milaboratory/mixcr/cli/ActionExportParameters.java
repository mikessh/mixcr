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

import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.mitools.cli.ActionParameters;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.export.FieldExtractor;
import com.milaboratory.mixcr.export.FieldExtractors;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Parameters(commandDescription = "Export binary data",
        optionPrefixes = "^")
public final class ActionExportParameters extends ActionParameters {
    final Class clazz;
    final String helpString;
    final String fieldsHelpString;

    protected ActionExportParameters(Class clazz) {
        this.clazz = clazz;
        ArrayList<String>[] description = new ArrayList[]{new ArrayList(), new ArrayList()};
        description[0].add("-h, --help");
        description[0].add(FIELDS_SHORT + ", " + FIELDS_LONG);
        description[0].add(PRESET_SHORT + ", " + PRESET_LONG);
        description[0].add(PRESET_FILE_SHORT + ", " + PRESET_FILE_LONG);
        description[1].add("print this help message");
        description[1].add("print available fields to export");
        description[1].add("preset parameters (full, min)");
        description[1].add("file with preset parameters");
        this.helpString =
                "Usage: export(Type) [options] input_file output_file\n" +
                        "Options:\n" +
                        Util.printTwoColumns(4, description[0], description[1], 20, 50, 5, "\n") + "\n" +
                        "Examples:\n" +
                        "    exportClones -p all -nFeature CDR1 input.clns output.txt\n" +
                        "    exportAlignments -pf params.txt -nFeature CDR1 -dAlignments input.clns output.txt\n";
        description = FieldExtractors.getDescription(clazz);
        this.fieldsHelpString = "Available export fields:\n" + Util.printTwoColumns(
                description[0], description[1], 20, 50, 5, "\n");

    }

    public Boolean fields = false;
    public String inputFile;
    public String outputFile;
    public ArrayList<FieldExtractor> exporters;

    public String printFieldsHelp() {
        return fieldsHelpString;
    }

    public String printHelp() {
        return helpString;
    }

    public final void parseParameters(String[] args) throws ParameterException {
        trim(args);
        for (String arg : args) {
            if (arg.equals(FIELDS_SHORT) || arg.equals(FIELDS_LONG)) {
                fields = true;
                return;
            }
            if (arg.equals("-h") || arg.equals("--help")) {
                help = true;
                return;
            }
        }
        if (args.length < 2)
            throw new ParameterException("No output file specified.");

        if (args.length == 2)
            exporters = getPresetParameters(clazz, "full");
        else
            exporters = parseParametersString(clazz, args, 0, args.length - 2);

        inputFile = args[args.length - 2];
        outputFile = args[args.length - 1];
    }


    public static ArrayList<FieldExtractor> parseParametersString(Class clazz, String[] args, int from, int to) {
        ArrayList<FieldExtractor> exporters = new ArrayList<>();
        ArrayList<String> exporter = new ArrayList<>();
        for (int i = from; i < to; ++i) {
            String arg = args[i];
            if (arg.charAt(0) == '-') {
                if (!exporter.isEmpty()) {
                    FieldExtractor exp = FieldExtractors.parse(clazz, exporter.toArray(new String[exporter.size()]));
                    if (exp == null)
                        throw new ParameterException("Unknown export field: " + exporter);
                    exporters.add(exp);
                    exporter.clear();
                }
                if (isPresetParameter(arg)) {
                    if (i == to - 1 || args[i + 1].charAt(0) == '-')
                        throw new ParameterException("Preset value not specified.");
                    exporters.addAll(getPresetParameters(clazz, args[++i]));
                } else if (isPresetFileParameter(arg)) {
                    if (i == to - 1 || args[i + 1].charAt(0) == '-')
                        throw new ParameterException("Preset file name not specified.");
                    try {
                        exporters.addAll(readFromFile(clazz, args[++i]));
                    } catch (IOException e) {
                        throw new ParameterException(e.getMessage());
                    }
                } else
                    exporter.add(arg);
            } else
                exporter.add(arg);
        }
        if (!exporter.isEmpty()) {
            FieldExtractor exp = FieldExtractors.parse(clazz, exporter.toArray(new String[exporter.size()]));
            if (exp == null)
                throw new ParameterException("Unknown export field: " + exporter);
            exporters.add(exp);
        }
        return exporters;
    }

    static List<FieldExtractor> readFromFile(Class clazz, String fileName) throws IOException {
        File file = new File(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        List<FieldExtractor> exporters = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty())
                continue;
            line = line.trim();
            if (line.startsWith("#"))
                continue;
            String s = line;
            do {
                line = s;
                s = line.replace("  ", " ");
            } while (s.length() != line.length());
            String[] exporter = line.split(" ");
            exporters.add(FieldExtractors.parse(clazz, exporter));
        }
        return exporters;
    }

    static ArrayList<FieldExtractor> parsePresetString(Class clazz, String string) {
        string = string.replace("\n", " ").trim();
        String s = string;
        do {
            string = s;
            s = string.replace("  ", " ");
        } while (s.length() != string.length());
        String[] args = string.split(" ");
        return parseParametersString(clazz, args, 0, args.length);
    }

    private static final Map<Class, Map<String, ArrayList<FieldExtractor>>> preset;

    static {
        preset = new HashMap<>();
        Map<String, ArrayList<FieldExtractor>> clones = new HashMap<>();
        clones.put("min", parsePresetString(Clone.class,
                "-count -vHit -dHit -jHit -cHit -nfeature CDR3"));
        clones.put("full", parsePresetString(Clone.class,
                "-count -fraction -sequence -quality " +
                        "-vHits -dHits -jHits -cHits " +
                        "-vAlignments -dAlignments -jAlignments -cAlignments " +
                        "-nFeature FR1 -minFeatureQuality FR1 -nFeature CDR1 -minFeatureQuality CDR1 " +
                        "-nFeature FR2 -minFeatureQuality FR2 -nFeature CDR2 -minFeatureQuality CDR2 " +
                        "-nFeature FR3 -minFeatureQuality FR3 -nFeature CDR3 -minFeatureQuality CDR3 " +
                        "-nFeature FR4 -minFeatureQuality FR4 " +
                        "-aaFeature FR1 -aaFeature CDR1 -aaFeature FR2 -aaFeature CDR2 -aaFeature FR3 -aaFeature CDR3 -aaFeature FR4 "));
        clones.put("vdjtools", parsePresetString(Clone.class,
                "-count -fraction -nFeature CDR3 -aaFeature CDR3 -vHit -dHit -jHit -vEnd -dStart -dEnd -jStart -vAlignment"));
        preset.put(Clone.class, clones);

        Map<String, ArrayList<FieldExtractor>> alignments = new HashMap<>();
        alignments.put("min", parsePresetString(VDJCAlignments.class,
                "-vHit -dHit -jHit -cHit -nfeature CDR3"));
        alignments.put("full", parsePresetString(VDJCAlignments.class,
                "-sequence -quality " +
                        "-vHits -dHits -jHits -cHits " +
                        "-vAlignments -dAlignments -jAlignments -cAlignments " +
                        "-nFeature FR1 -minFeatureQuality FR1 -nFeature CDR1 -minFeatureQuality CDR1 " +
                        "-nFeature FR2 -minFeatureQuality FR2 -nFeature CDR2 -minFeatureQuality CDR2 " +
                        "-nFeature FR3 -minFeatureQuality FR3 -nFeature CDR3 -minFeatureQuality CDR3 " +
                        "-nFeature FR4 -minFeatureQuality FR4 " +
                        "-aaFeature FR1 -aaFeature CDR1 -aaFeature FR2 -aaFeature CDR2 -aaFeature FR3 -aaFeature CDR3 -aaFeature FR4 "));
        preset.put(VDJCAlignments.class, alignments);
    }

    public static ArrayList<FieldExtractor> getPresetParameters(Class clazz, String string) {
        return preset.get(clazz).get(string);
    }

    private static void trim(String[] args) {
        for (int i = 0; i < args.length; i++)
            args[i] = args[i].trim();
    }

    public static final String PRESET_SHORT = "-p",
            PRESET_LONG = "--preset",
            PRESET_FILE_SHORT = "-pf",
            PRESET_FILE_LONG = "--presetFile",
            FIELDS_SHORT = "-l",
            FIELDS_LONG = "--listFields";

    public static boolean isPresetParameter(String string) {
        return string.equals(PRESET_SHORT) || string.equals(PRESET_LONG);
    }

    public static boolean isPresetFileParameter(String string) {
        return string.equals(PRESET_FILE_SHORT) || string.equals(PRESET_FILE_LONG);
    }
}
