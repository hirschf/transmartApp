package com.recomdata.transmart.data.export

import org.transmartproject.core.dataquery.TabularResult
import org.transmartproject.core.dataquery.highdim.AssayColumn
import org.transmartproject.core.dataquery.highdim.BioMarkerDataRow
import org.transmartproject.core.dataquery.highdim.HighDimensionDataTypeResource
import org.transmartproject.core.dataquery.highdim.assayconstraints.AssayConstraint
import org.transmartproject.core.dataquery.highdim.projections.AllDataProjection
import org.transmartproject.core.dataquery.highdim.projections.Projection

/**
 * Created by jan on 1/20/14.
 */
class HighDimExportService {

    /**
     * This are the headers that are used in the tab-separated export files for each field type. Before export, they are
     * captialised.
     */
    Map dataFieldHeaders = [
            rawIntensity: 'value',
            intensity: 'value',
            value: 'value',
            logIntensity: 'log2e',
            zscore: 'zscore'
    ]
    Map rowFieldHeaders = [
            geneSymbol: 'gene symbol',
            geneId: 'gene id',
            mirnaId: 'mirna id',
            peptide: 'peptide sequence',
            antigenName: 'analyte name',
            uniprotId: 'uniprot id',
            uniprotName: 'uniprot name',
            transcriptId: 'transcript id',
            probe: 'probe id',
            probeId: 'probe id'
    ]

    def highDimensionResourceService

    def exportHighDimData(Map args) {
        // boolean splitAttributeColumn
        // String (but really a number) resultInstanceId
        // List<String> conceptPaths
        // String dataType
        // String studyDir

        // dataType one of: mrna, mirna, protein, rbm, rnaseqcog, (metabolomics)

//        // static input for now: a resultInstanceId and a list of concept paths
//        def resultInstanceId = 23306  //22967
//        def conceptPaths = [/\\Public Studies\Public Studies\GSE8581\MRNA\Biomarker Data\Affymetrix Human Genome U133A 2.0 Array\Lung/]


        /*
         per-datatype fields we need to export:

         mrna: (trialName), rawIntensity->value zscore logIntensity->log2e / probe (id), geneSymbol, geneId
         mirna: rawIntensity, logIntensity, zscore / mirnaId
         protein: intensity, zscore / peptide -> peptide sequence, unitProtId
         rbm: value, zscore / antigenName, uniprotId   ---gplId, antigenName, uniprotId, geneSymbol, geneId
         rnaseqcog: rawIntensity, zscore / transcriptId, geneSymbol, geneId
         metabolomics: (not implemented in coredb) ?? / biochemical name, hmdbId

         */

        String dataType = args.dataType
        boolean splitAttributeColumn = args.get('splitAttributeColumn', false)
        def resultInstanceId = args.resultInstanceId
        List<String> conceptPaths = args.conceptPaths
        String studyDir = args.studyDir


        HighDimensionDataTypeResource dataTypeResource = highDimensionResourceService.getSubResourceForType(dataType)

        def assayconstraints = []

        assayconstraints << dataTypeResource.createAssayConstraint(
                AssayConstraint.PATIENT_SET_CONSTRAINT,
                result_instance_id: resultInstanceId)

        assayconstraints << dataTypeResource.createAssayConstraint(
                AssayConstraint.DISJUNCTION_CONSTRAINT,
                subconstraints:
                        [(AssayConstraint.ONTOLOGY_TERM_CONSTRAINT): conceptPaths.collect {[concept_key: it]}])

        AllDataProjection projection = dataTypeResource.createProjection(Projection.ALL_DATA_PROJECTION)

        String[] header = ['PATIENT ID']
        header += splitAttributeColumn ? ["SAMPLE TYPE", "TIMEPOINT", "TISSUE TYPE", "GPL ID"] : ["SAMPLE"]
        header += ["ASSAY ID", "SAMPLE CODE"]

        Map<String, String> dataKeys = projection.dataProperties.collectEntries {[it, dataFieldHeaders.get(it, it).toUpperCase()]}

        Map<String, String> rowKeys = projection.rowProperties.collectEntries {[it, rowFieldHeaders.get(it, it).toUpperCase()]}

        header += dataKeys.values()
        header += rowKeys.values()


        Writer writer = null
        String fileName = null
        TabularResult<AssayColumn, BioMarkerDataRow<Map<String, String>>> tabularResult = null
        long rowsFound = 0
        long startTime
        try {
            File outputFile = new File(studyDir, dataType+'.trans')
            fileName = outputFile.getAbsolutePath()
            writer = outputFile.newWriter(true)

            log.info("start sample retrieving query")

            startTime = System.currentTimeMillis()

            tabularResult = dataTypeResource.retrieveData(assayconstraints, [], projection)

            List<AssayColumn> assayList = tabularResult.indicesList

            log.info("started file writing to $fileName")
            writer << header.join('\t') << '\n'

            writeloop:
            for (BioMarkerDataRow<Map<String, String>> datarow : tabularResult) {
                for (AssayColumn assay : assayList) {
                    rowsFound++
                    //if (rowsFound > 20) break writeloop

                    Map<String, String> data = datarow[assay]

                    // TODO: This probably shouldn't happen, but it does!
                    if (data == null) break

                    String assayId =        assay.id
                    String patientId =      assay.patientInTrialId
                    String sampleTypeName = assay.sampleType.label
                    String timepointName =  assay.timepoint.label
                    String tissueTypeName = assay.tissueType.label
                    String platform =       assay.platform.id
                    String sampleCode =     assay.sampleCode

                    List<String> line = [patientId]
                    if (splitAttributeColumn)
                    //       SAMPLE TYPE     TIMEPOINT      TISSUE TYPE     GPL ID
                        line += [sampleTypeName, timepointName, tissueTypeName, platform]
                    else
                    //      SAMPLE
                        line << [sampleTypeName, timepointName, tissueTypeName, platform].grep().join('_')

                    line << assayId << sampleCode

                    for (String dataField: dataKeys.keySet()) {
                        line << data[dataField]
                    }

                    for (String rowField: rowKeys.keySet()) {
                        line << datarow."$rowField"
                    }

                    writer << line.join('\t') << '\n'

                }
            }
            if (!rowsFound) {
                log.error("No data found while trying to export $dataType data")
                if (!outputFile.delete())
                    log.error("Unable to delete empty output file $fileName")
            }
            log.info("Retrieving data took ${System.currentTimeMillis() - startTime} ms")

        } finally {
            writer?.close()
            tabularResult?.close()
        }

        return [outFile: fileName, dataFound: rowsFound]
    }
}
