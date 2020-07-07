package org.evomaster.core.search.gene

import org.apache.commons.lang3.StringEscapeUtils
import org.evomaster.client.java.instrumentation.shared.StringSpecialization.*
import org.evomaster.client.java.instrumentation.shared.StringSpecializationInfo
import org.evomaster.client.java.instrumentation.shared.TaintInputName
import org.evomaster.core.Lazy
import org.evomaster.core.StaticCounter
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.output.OutputFormat
import org.evomaster.core.parser.RegexHandler
import org.evomaster.core.parser.RegexUtils
import org.evomaster.core.search.gene.GeneUtils.EscapeMode
import org.evomaster.core.search.gene.GeneUtils.getDelta
import org.evomaster.core.search.impact.impactinfocollection.GeneImpact
import org.evomaster.core.search.impact.impactinfocollection.value.StringGeneImpact
import org.evomaster.core.search.service.AdaptiveParameterControl
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.EvaluatedMutation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.evomaster.core.search.service.mutator.MutationWeightControl
import org.evomaster.core.search.service.mutator.genemutation.AdditionalGeneSelectionInfo
import org.evomaster.core.search.service.mutator.genemutation.ArchiveGeneMutator
import org.evomaster.core.search.service.mutator.genemutation.SubsetGeneSelectionStrategy
import org.evomaster.core.search.service.mutator.genemutation.archive.GeneArchieMutationInfo
import org.evomaster.core.search.service.mutator.genemutation.archive.StringGeneArchiveMutationInfo


class StringGene(
        name: String,
        var value: String = "foo",
        /** Inclusive */
        val minLength: Int = 0,
        /** Inclusive */
        val maxLength: Int = 16,
        /**
         * Depending on what a string is representing, there might be some chars
         * we do not want to use.
         * For example, in a URL Path variable, we do not want have "/", as otherwise
         * it would create 2 distinct paths
         */
        val invalidChars: List<Char> = listOf(),

        val mutationInfo : GeneArchieMutationInfo = GeneArchieMutationInfo()

) : Gene(name) {

    companion object {

        private val log: Logger = LoggerFactory.getLogger(StringGene::class.java)

        private const val PROB_CHANGE_SPEC = 0.1

        /**
         * These are regex with no value, as they match everything.
         * Note: we could have something more sophisticated, to check for any possible meaningless one.
         * But this simple list should do for most cases.
         */
        private val meaninglesRegex = setOf(".*","(.*)","^(.*)","(.*)$","^(.*)$","^((.*))","((.*))$","^((.*))$")
    }

    /*
        Even if through mutation we can get large string, we should
        avoid sampling very large strings by default
     */
    private val maxForRandomization = 16

    private var validChar: String? = null

    /**
     * Based on taint analysis, in some cases we can determine how some Strings are
     * used in the SUT.
     * For example, if a String is used as a Date, then it make sense to use a specialization
     * in which we mutate to have only Strings that are valid dates
     */
    private val specializations: MutableSet<StringSpecializationInfo> = mutableSetOf()

    var specializationGenes: MutableList<Gene> = mutableListOf()

    var selectedSpecialization = -1

    var selectionUpdatedSinceLastMutation = false

    /**
     * Check if we already tried to use this string for taint analysis
     */
    var tainted = false


    /**
     * During the search, we might discover (with TaintAnalysis) that 2 different
     * string variables are compared for equality.
     * In those cases, if we want to keep them in sync, each time we mutate one, we
     * need to update the other.
     * This is not trivial, as the strings might be subject to different constraints,
     * and we would need to find their intersection.
     */
    var bindingIds = mutableSetOf<String>()



    override fun copy(): Gene {
        val copy = StringGene(name, value, minLength, maxLength, invalidChars, mutationInfo.clone())
                .also {
                    it.specializationGenes = this.specializationGenes.map { g -> g.copy() }.toMutableList()
                    it.specializations.addAll(this.specializations)
                    it.validChar = this.validChar
                    it.selectedSpecialization = this.selectedSpecialization
                    it.selectionUpdatedSinceLastMutation = this.selectionUpdatedSinceLastMutation
                    it.tainted = this.tainted
                    it.bindingIds = this.bindingIds.map { id -> id }.toMutableSet()
                }
        copy.specializationGenes.forEach { it.parent = copy }
        return copy
    }

    fun getSpecializationGene(): Gene? {
        if (selectedSpecialization >= 0 && selectedSpecialization < specializationGenes.size) {
            return specializationGenes[selectedSpecialization]
        }
        return null
    }

    override fun isMutable(): Boolean {
        if (getSpecializationGene() != null) {
            return specializationGenes.size > 1 || getSpecializationGene()!!.isMutable()
        }
        return true
    }

    override fun randomize(randomness: Randomness, forceNewValue: Boolean, allGenes: List<Gene>) {
        value = randomness.nextWordString(minLength, Math.min(maxLength, maxForRandomization))
        repair()
        selectedSpecialization = -1
        handleBinding(allGenes)
    }

    override fun mutate(randomness: Randomness, apc: AdaptiveParameterControl, mwc: MutationWeightControl, allGenes: List<Gene>, selectionStrategy: SubsetGeneSelectionStrategy, enableAdaptiveGeneMutation: Boolean, additionalGeneMutationInfo: AdditionalGeneSelectionInfo?) : Boolean{

        if (enableAdaptiveGeneMutation){
            additionalGeneMutationInfo?:throw IllegalArgumentException("additionalGeneMutationInfo should not be null when enable adaptive gene mutation")
            //TODO consider bindingIds
            additionalGeneMutationInfo.archiveGeneMutator.mutate(this, additionalGeneMutationInfo.targets)
            return true
        }

        val specializationGene = getSpecializationGene()

        if (specializationGene == null && specializationGenes.isNotEmpty() && randomness.nextBoolean(0.5)) {
            selectedSpecialization = randomness.nextInt(0, specializationGenes.size - 1)
            selectionUpdatedSinceLastMutation = false
            handleBinding(allGenes)
            return true

        } else if (specializationGene != null) {
            if (selectionUpdatedSinceLastMutation && randomness.nextBoolean(0.5)) {
                /*
                    selection of most recent added gene, but only with a given
                    probability, albeit high.
                    point is, switching is not always going to be beneficial
                 */
                selectedSpecialization = specializationGenes.lastIndex
            } else if (specializationGenes.size > 1 && randomness.nextBoolean(PROB_CHANGE_SPEC)) {
                //choose another specialization, but with low probability
                selectedSpecialization = randomness.nextInt(0, specializationGenes.size - 1, selectedSpecialization)
            } else if(randomness.nextBoolean(PROB_CHANGE_SPEC)){
                //not all specializations are useful
                selectedSpecialization = -1
            } else {
                //extract impact of specialization of String
                val impact = if (enableAdaptiveGeneMutation || selectionStrategy != SubsetGeneSelectionStrategy.DEFAULT)
                    (additionalGeneMutationInfo?.impact as? StringGeneImpact)?.specializationGeneImpact?.get(selectedSpecialization) as? GeneImpact
                    else null
                //just mutate current selection
                specializationGene.standardMutation(randomness, apc, mwc, allGenes, selectionStrategy, enableAdaptiveGeneMutation, additionalGeneMutationInfo?.copyFoInnerGene(impact = impact))
            }
            selectionUpdatedSinceLastMutation = false
            handleBinding(allGenes)
            return true
        }

        val minPforTaint = 0.1
        val tp = apc.getBaseTaintAnalysisProbability(minPforTaint)

        if (
                !apc.doesFocusSearch() &&
                (
                        (!tainted && randomness.nextBoolean(tp))
                                ||
                                /*
                                    if this has already be tainted, but that lead to no specialization,
                                    we do not want to reset with a new taint value, and so skipping all
                                    standard mutation on strings.
                                    but we might want to use a taint value at a later stage, in case its
                                    specialization depends on code paths executed depending on other inputs
                                    in the test case
                                 */
                                (tainted && randomness.nextBoolean(Math.max(tp/2, minPforTaint)))
                        )
        ) {

            value = TaintInputName.getTaintName(StaticCounter.getAndIncrease())
            tainted = true
            return true
        }

        if (tainted && randomness.nextBoolean(0.5) && TaintInputName.isTaintInput(value)) {
            randomize(randomness, true, allGenes)
            return true
        }

        val p = randomness.nextDouble()
        val s = value

        /*
            What type of mutations we do on Strings is strongly
            correlated on how we define the fitness functions.
            When dealing with equality, as we do left alignment,
            then it makes sense to prefer insertion/deletion at the
            end of the strings, and reward more "change" over delete/add
         */

        val others = allGenes.flatMap { it.flatView() }
                .filterIsInstance<StringGene>()
                .map { it.getValueAsRawString() }
                .filter { it != value }
                .filter { !TaintInputName.isTaintInput(it) }

        value = when {
            //seeding: replace
            p < 0.02 && !others.isEmpty() -> {
                randomness.choose(others)
            }
            //change
            p < 0.8 && s.isNotEmpty() -> {
                val delta = getDelta(randomness, apc, start = 6, end = 3)
                val sign = randomness.choose(listOf(-1, +1))
                log.trace("Changing char in: {}", s)
                val i = randomness.nextInt(s.length)
                val array = s.toCharArray()
                array[i] = s[i] + (sign * delta)
                String(array)
            }
            //delete last
            p < 0.9 && s.isNotEmpty() && s.length > minLength -> {
                s.dropLast(1)
            }
            //append new
            s.length < maxLength -> {
                if (s.isEmpty() || randomness.nextBoolean(0.8)) {
                    s + randomness.nextWordChar()
                } else {
                    log.trace("Appending char")
                    val i = randomness.nextInt(s.length)
                    if (i == 0) {
                        randomness.nextWordChar() + s
                    } else {
                        s.substring(0, i) + randomness.nextWordChar() + s.substring(i, s.length)
                    }
                }
            }
            else -> {
                //do nothing
                s
            }
        }

        repair()
        handleBinding(allGenes)
        return true
    }

    /**
     * This should be called after each mutation, to check if any other genes must be updated after
     * this one has been mutated
     */
    private fun handleBinding(allGenes: List<Gene>){

        if(bindingIds.isEmpty()){
            return
        }

        val others = allGenes.filterIsInstance<StringGene>()
                .filter { it != this }
                .filter{ k ->  this.bindingIds.any { k.bindingIds.contains(it) }}

        if(others.isEmpty()){
            /*
                this could happen if the structure mutator did remove the actions
                containing these other genes
             */
            return
        }

        /*
            TODO doing this "properly" will be a lot of work... for now, we keep it simple,
            and remove the specialization in the others
         */
        val update = getValueAsRawString()
        for (k in others){
            k.selectedSpecialization = -1
            k.value = update
        }
    }

    fun addSpecializations(key: String, specs: Collection<StringSpecializationInfo>, randomness: Randomness) {

        val toAddSpecs = specs
                //don't add the same specialization twice
                .filter { !specializations.contains(it) }
                /*
                        a StringGene might have some characters that are not allowed,
                        like '/' and '.' in a PathParam.
                        If we have a constant that uses any of such chars, then we must
                        skip it.
                        We allow constant larger than Max (as that should not be a problem),
                        but not smaller than Min (eg to avoid empty strings in PathParam)
                 */
                .filter { s ->
                    s.stringSpecialization != CONSTANT ||
                            (invalidChars.none { c -> s.value.contains(c) } && s.value.length >= minLength)
                }

        val toAddGenes = mutableListOf<Gene>()

        //all constant values are merged in the same enum gene
        if (toAddSpecs.any { it.stringSpecialization == CONSTANT }) {
            /*
                TODO partial matches
             */
            toAddGenes.add(
                    EnumGene<String>(
                            name,
                            toAddSpecs.filter { it.stringSpecialization == CONSTANT }.map { it.value }))
        }

        if (toAddSpecs.any { it.stringSpecialization == CONSTANT_IGNORE_CASE }) {
            toAddGenes.add(RegexHandler.createGeneForJVM(
                    toAddSpecs.filter { it.stringSpecialization == CONSTANT_IGNORE_CASE }
                            .map { "^(${RegexUtils.ignoreCaseRegex(it.value)})$" }
                            .joinToString("|")
            ))
        }


        if (toAddSpecs.any { it.stringSpecialization == DATE_YYYY_MM_DD }) {
            toAddGenes.add(DateGene(name))
        }

        if (toAddSpecs.any { it.stringSpecialization == BOOLEAN }) {
            toAddGenes.add(BooleanGene(name))
        }

        if (toAddSpecs.any { it.stringSpecialization == INTEGER }) {
            toAddGenes.add(IntegerGene(name))
        }

        if (toAddSpecs.any { it.stringSpecialization == LONG }) {
            toAddGenes.add(LongGene(name))
        }

        if (toAddSpecs.any { it.stringSpecialization == FLOAT }) {
            toAddGenes.add(FloatGene(name))
        }

        if (toAddSpecs.any { it.stringSpecialization == DOUBLE }) {
            toAddGenes.add(DoubleGene(name))
        }

        //all regex are combined with disjunction in a single gene
        handleRegex(key, toAddSpecs, toAddGenes)

        /*
            TODO
            here we could check if merging with existing genes.
            - CONSTANT would be relative easy, as just creating a new enum with the union of all constants
            - REGEX would be tricky, because rather than a disjunction, it would likely need to be an AND,
              which could be achieved with "(?=)". But that is something we do not support yet in the
              grammar, and likely it is VERY complicated to do... eg, see:
              https://stackoverflow.com/questions/24102484/can-regex-match-intersection-between-two-regular-expressions
         */

        if (toAddGenes.size > 0) {
            selectionUpdatedSinceLastMutation = true
            toAddGenes.forEach {
                it.randomize(randomness, false, listOf())
                it.parent = this
            }
            specializationGenes.addAll(toAddGenes)
            specializations.addAll(toAddSpecs)
        }

        if (toAddSpecs.any { it.stringSpecialization == EQUAL }) {
            /*
                this treated specially. we do not create a new string specialization, but
                rather update bindingIds
             */
            val ids = toAddSpecs.filter { it.stringSpecialization == EQUAL }.map { it.value }
            bindingIds.addAll(ids)
        }
    }


    private fun handleRegex(key: String, toAddSpecs: List<StringSpecializationInfo>, toAddGenes: MutableList<Gene>) {

        val fullPredicate = { s: StringSpecializationInfo -> s.stringSpecialization == REGEX && s.type.isFullMatch }
        val partialPredicate = { s: StringSpecializationInfo -> s.stringSpecialization == REGEX && s.type.isPartialMatch }

        if (toAddSpecs.any(fullPredicate)) {
            val regex = toAddSpecs
                    .filter(fullPredicate)
                    .filter{isMeaningfulRegex(it.value)}
                    .map { it.value }
                    .joinToString("|")

            try {
                toAddGenes.add(RegexHandler.createGeneForJVM(regex))
            } catch (e: Exception) {
                LoggingUtil.uniqueWarn(log, "Failed to handle regex: $regex")
            }
        }

/*
    Handling a partial match on a single gene is quite complicated to implement, plus
    it might not be so useful.
    TODO something to investigate in the future if we end up with some of these cases
 */
//        if(toAddSpecs.any(partialPredicate)){
//            val regex = toAddSpecs
//                    .filter(partialPredicate)
//                    .map { RegexUtils.extractPartialRegex(key, this.getValueAsRawString(), it.value) }
//                    .joinToString("|")
//            toAddGenes.add(RegexHandler.createGeneForJVM(regex))
//        }
    }


    private fun isMeaningfulRegex(regex: String):  Boolean {

        return ! meaninglesRegex.contains(regex)
    }


    /**
     * Make sure no invalid chars is used
     */
    fun repair() {
        if (invalidChars.isEmpty()) {
            //nothing to do
            return
        }

        if (validChar == null) {
            //compute a valid char
            for (c in 'a'..'z') {
                if (!invalidChars.contains(c)) {
                    validChar = c.toString()
                    break
                }
            }
        }
        if (validChar == null) {
            //no basic char is valid??? TODO should handle this situation, although likely never happens
            return
        }

        for (invalid in invalidChars) {
            value = value.replace("$invalid", validChar!!)
        }
    }

    override fun getValueAsPrintableString(previousGenes: List<Gene>, mode: EscapeMode?, targetFormat: OutputFormat?): String {

        val specializationGene = getSpecializationGene()

        if (specializationGene != null) {
            return "\"" + specializationGene.getValueAsRawString() + "\""
        }

        val rawValue = getValueAsRawString()
        if (mode != null && mode == EscapeMode.XML) {
            return StringEscapeUtils.escapeXml(rawValue)
        } else {
            when {
                // TODO this code should be refactored with other getValueAsPrintableString() methods
                (targetFormat == null) -> return "\"${rawValue}\""
                //"\"${rawValue.replace("\"", "\\\"")}\""
                (mode != null) -> return "\"${GeneUtils.applyEscapes(rawValue, mode, targetFormat)}\""
                else -> return "\"${GeneUtils.applyEscapes(rawValue, EscapeMode.TEXT, targetFormat)}\""
            }

        }
    }

    override fun getValueAsRawString(): String {
        val specializationGene = getSpecializationGene()

        if (specializationGene != null) {
            return specializationGene.getValueAsRawString()
        }
        return value
    }

    override fun copyValueFrom(other: Gene) {
        if (other !is StringGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        this.value = other.value
        this.selectedSpecialization = other.selectedSpecialization

        this.specializations.clear()
        this.specializations.addAll(other.specializations)

        this.specializationGenes.clear()
        this.specializationGenes.addAll(other.specializationGenes.map { it.copy() })

        this.tainted = other.tainted

        this.bindingIds.clear()
        this.bindingIds.addAll(other.bindingIds)
    }

    override fun containsSameValueAs(other: Gene): Boolean {
        if (other !is StringGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }

        val tg = this.getSpecializationGene()
        val og = other.getSpecializationGene()

        if ((tg == null && og != null) ||
                (tg != null && og == null)) {
            return false
        }

        if (tg != null) {
            return tg.containsSameValueAs(og!!)
        }

        return this.value == other.value
    }

    override fun flatView(excludePredicate: (Gene) -> Boolean): List<Gene> {
        return if (excludePredicate(this)) listOf(this)
        else listOf(this).plus(specializationGenes.flatMap { it.flatView(excludePredicate) })
    }

    override fun reachOptimal(targets: Set<Int>): Boolean {
        return mutationInfo.reachOptimal(targets)
    }

    override fun archiveMutationUpdate(original: Gene, mutated: Gene, targetsEvaluated: Map<Int, EvaluatedMutation>, archiveMutator: ArchiveGeneMutator) {
        if (targetsEvaluated.isEmpty()) return

        original as? StringGene ?: throw IllegalStateException("$original should be StringGene")
        mutated as? StringGene ?: throw IllegalStateException("$mutated should be StringGene")

        val previousValue = original.value
        val mutatedValue = mutated.value

        val doLengthMutation = previousValue.length != mutatedValue.length

        val diffIndex = mutableListOf<Int>()
        if (!doLengthMutation){
            mutatedValue.toCharArray().forEachIndexed { index, c ->
                if (c != previousValue[index])
                    diffIndex.add(index)
            }
        }

        if (!doLengthMutation && diffIndex.isEmpty())
            log.warn("charMutation (applied = {}) and lengthMutation (applied{}) are not recommended to apply at same time.", diffIndex.isNotEmpty(), doLengthMutation)


        targetsEvaluated.forEach { (t, u) ->

            val archiveMutationInfo = mutationInfo.getArchiveMutationInfo(this, t, archiveMutator) as? StringGeneArchiveMutationInfo ?: throw IllegalStateException("mutation info for StringGene should be StringGeneArchiveMutationInfo")

            archiveMutationInfo.synCharMutation(value, doLengthMutation, u.isImproved(), archiveMutator)

            if (doLengthMutation) {
                //gene is mutated at first time
                archiveMutationInfo.lengthUpdate(previous = previousValue, mutated = mutatedValue, mutatedBetter = u.isImproved(), archiveMutator = archiveMutator, template = this)
            } else {
                if (diffIndex.isEmpty()){
                    log.info("nothing to mutate for the gene {}", mutatedValue)
                }else if (diffIndex.size > 1){
                    log.info("multiple chars are mutated from {} to {}", previousValue, mutatedValue)
                }
                archiveMutationInfo.charUpdate(previous = previousValue, mutated = mutatedValue, diffIndex = diffIndex, invalidChars = invalidChars,
                        mutatedBetter = u.isImproved(), archiveMutator = archiveMutator)
            }
            Lazy.assert {
                archiveMutationInfo.charsMutation.size >= value.length
            }
        }
    }

    override fun mutationWeight(): Double {
        return if(specializationGenes.isEmpty()) 1.0 else (specializationGenes.map { it.mutationWeight() }.sum() * PROB_CHANGE_SPEC + 1.0)
    }
}