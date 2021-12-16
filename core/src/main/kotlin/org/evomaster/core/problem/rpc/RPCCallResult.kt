package org.evomaster.core.problem.rpc

import com.google.common.annotations.VisibleForTesting
import org.evomaster.client.java.controller.api.dto.CustomizedCallResultCode
import org.evomaster.client.java.controller.api.dto.problem.rpc.RPCExceptionInfoDto
import org.evomaster.client.java.controller.api.dto.problem.rpc.exception.RPCExceptionType
import org.evomaster.core.search.Action
import org.evomaster.core.search.ActionResult

/**
 * define RPC call result with various situations,
 *  eg, success, exception, potential bug, fail (some problems when invoking the call, eg, timeout, network)
 */
class RPCCallResult : ActionResult {

    companion object {
        const val LAST_STATEMENT_WHEN_INTERNAL_ERROR = "LAST_STATEMENT_WHEN_INTERNAL_ERROR"
        const val INVOCATION_CODE = "INVOCATION_CODE"
        const val CUSTOM_EXP_BODY = "CUSTOM_EXP_BODY"
        const val EXCEPTION_CODE = "EXCEPTION_CODE"
        const val CUSTOM_BUSINESS_LOGIC_CODE = "CUSTOM_BUSINESS_LOGIC_CODE"
        const val CUSTOM_BUSINESS_LOGIC_SUCCESS = 200
        const val CUSTOM_BUSINESS_LOGIC_SERVICE_ERROR = 500
        const val CUSTOM_BUSINESS_LOGIC_OTHERWISE_ERROR = 400
    }

    constructor(stopping: Boolean = false) : super(stopping)

    @VisibleForTesting
    internal constructor(other: ActionResult) : super(other)

    override fun copy(): ActionResult {
        return RPCCallResult(this)
    }

    fun setFailedCall(){
        addResultValue(INVOCATION_CODE, RPCCallResultCategory.FAILED.name)
    }

    fun failedCall(): Boolean{
        return getInvocationCode() != RPCCallResultCategory.HANDLED.name
    }

    fun setSuccess(){
        addResultValue(INVOCATION_CODE, RPCCallResultCategory.HANDLED.name)
    }

    fun getInvocationCode(): String?{
        return getResultValue(INVOCATION_CODE)
    }

    fun getExceptionCode() = getResultValue(EXCEPTION_CODE)

    fun setLastStatementForInternalError(info: String){
        addResultValue(LAST_STATEMENT_WHEN_INTERNAL_ERROR, info)
    }

    fun setCustomizedBusinessLogicCode(result: CustomizedCallResultCode){
        when(result){
            CustomizedCallResultCode.SUCCESS -> addResultValue(CUSTOM_BUSINESS_LOGIC_CODE, CUSTOM_BUSINESS_LOGIC_SUCCESS.toString())
            CustomizedCallResultCode.SERVICE_ERROR -> addResultValue(CUSTOM_BUSINESS_LOGIC_CODE, CUSTOM_BUSINESS_LOGIC_SERVICE_ERROR.toString())
            CustomizedCallResultCode.OTHERWISE_ERROR -> addResultValue(CUSTOM_BUSINESS_LOGIC_CODE, CUSTOM_BUSINESS_LOGIC_OTHERWISE_ERROR.toString())
        }

    }

    fun isSuccessfulBusinessLogicCode() = getResultValue(CUSTOM_BUSINESS_LOGIC_CODE) == CUSTOM_BUSINESS_LOGIC_SUCCESS.toString()
    fun isCustomizedServiceError() = getResultValue(CUSTOM_BUSINESS_LOGIC_CODE) == CUSTOM_BUSINESS_LOGIC_SERVICE_ERROR.toString()
    fun isOtherwiseCustomizedServiceError() = getResultValue(CUSTOM_BUSINESS_LOGIC_CODE)  == CUSTOM_BUSINESS_LOGIC_OTHERWISE_ERROR.toString()

    fun getLastStatementForPotentialBug() = getResultValue(LAST_STATEMENT_WHEN_INTERNAL_ERROR)

    fun setRPCException(dto: RPCExceptionInfoDto) {

        if (dto.type != null){
            val code = when(dto.type){
                RPCExceptionType.APP_INTERNAL_ERROR -> RPCCallResultCategory.INTERNAL_ERROR
                RPCExceptionType.UNEXPECTED_EXCEPTION -> RPCCallResultCategory.UNEXPECTED_EXCEPTION
                RPCExceptionType.CUSTOMIZED_EXCEPTION-> RPCCallResultCategory.CUSTOM_EXCEPTION
                else -> RPCCallResultCategory.OTHERWISE_EXCEPTION
            }

            addResultValue(EXCEPTION_CODE, dto.type.name)
            addResultValue(INVOCATION_CODE, code.name)

        }
    }

    fun setCustomizedExceptionBody(json: String){
        addResultValue(CUSTOM_EXP_BODY, json)
    }

    fun getCustomizedExceptionBody() = getResultValue(CUSTOM_EXP_BODY)

    override fun matchedType(action: Action): Boolean {
        return action is RPCCallAction
    }

    fun hasPotentialFault() : Boolean = getInvocationCode() == RPCCallResultCategory.INTERNAL_ERROR.name ||
            getInvocationCode() == RPCCallResultCategory.UNEXPECTED_EXCEPTION.name

}