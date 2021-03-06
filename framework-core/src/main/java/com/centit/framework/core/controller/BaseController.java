package com.centit.framework.core.controller;

import com.centit.framework.common.*;
import com.centit.support.algorithm.CollectionsOpt;
import com.centit.support.common.ObjectException;
import com.centit.support.database.utils.QueryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author sx
 * 2013年12月30日
 */

//@Controller
@SuppressWarnings("unused")
public abstract class BaseController {

    public static final String OBJLIST = "objList";
    public static final String PAGE_DESC = "pageDesc";

    protected Logger logger = LoggerFactory.getLogger(BaseController.class);
    /**
     * 当前log4j日志是否打开Debug模式
     */
    protected boolean logDebug = logger.isDebugEnabled();

    /*public  String getOptId(){
        return "NOT_DEFINED";
    }*/
    /**
     * 绑定所有字符串参数绑定，将Html字符转义 和日期参数转义
     *
     * @param binder {@link WebDataBinder}
     */
    @InitBinder
    protected void initBinder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(4096);
        binder.registerCustomEditor(String.class, new StringPropertiesEditor(true));
        binder.registerCustomEditor(Date.class, new DatePropertiesEditor());
        binder.registerCustomEditor(java.sql.Date.class, new SqlDatePropertiesEditor());
        binder.registerCustomEditor(java.sql.Timestamp.class, new SqlTimestampPropertiesEditor());
    }

    /**
     * 前端统一异常处理，
     *
     * @param ex {@link BindException}
     * @param request request
     * @param response response
     * @throws IOException IOException
     */
    //(value = {BindException.class,MethodArgumentNotValidException.class})
    @ExceptionHandler
    public void exceptionHandler(Exception ex, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (ex == null){
            logger.error("未知错误，cause by " + request.getRequestURL().toString());
            return;
        }
        if (WebOptUtils.isAjax(request)) {
            if (ex instanceof ObjectException){
                logger.error(ex.getMessage());
                ObjectException objex = (ObjectException)ex;
                if(WebOptUtils.exceptionNotAsHttpError &&
                    (objex.getExceptionCode() == ResponseData.ERROR_USER_NOT_LOGIN ||
                    objex.getExceptionCode() == ResponseData.ERROR_UNAUTHORIZED ) ){
                    JsonResultUtils.writeHttpErrorMessage(objex.getExceptionCode(),
                        objex.getLocalizedMessage(), response);
                }else {
                    ResponseSingleData responseData =
                        new ResponseSingleData(objex.getExceptionCode(),
                            ObjectException.extortExceptionMessage(objex));
                    responseData.setData(objex.getObjectData());
                    JsonResultUtils.writeResponseDataAsJson(responseData, response);
                }
                return;
            }
            logger.error(ObjectException.extortExceptionMessage(ex));
            BindingResult bindingResult = null;
            if(ex instanceof BindException){
                bindingResult =((BindException)ex).getBindingResult();
            }else if(ex instanceof MethodArgumentNotValidException){
                bindingResult =((MethodArgumentNotValidException)ex).getBindingResult();
            }
            if(bindingResult!=null){
                ResponseMapData responseData = new ResponseMapData(ResponseData.ERROR_BAD_REQUEST);
                StringBuilder errMsg = new StringBuilder();

                if (bindingResult.hasErrors()) {
                    for (FieldError fieldError : bindingResult.getFieldErrors()) {
                        responseData.addResponseData(fieldError.getField(), fieldError.getDefaultMessage());
                        errMsg.append(fieldError.getField()).append("：")
                            .append(fieldError.getDefaultMessage()).append("；");
                    }
                }
                responseData.setMessage(errMsg.toString());
                JsonResultUtils.writeResponseDataAsJson(responseData, response);
                return;
            }
            // 如果是非绑定错误，需要显示抛出异常帮助前台调试错误
            JsonResultUtils.writeErrorMessageJson(ResponseData.ERROR_INTERNAL_SERVER_ERROR,
                ObjectException.extortExceptionMessage(ex), response);
        } else {
            if (ex instanceof ObjectException) {
                logger.error(ex.getMessage());
                ObjectException objex = (ObjectException) ex;
                response.sendRedirect(request.getContextPath()
                    + "/system/exception/error/" + objex.getExceptionCode());
            }else {
                logger.error(ObjectException.extortExceptionMessage(ex));
                response.sendRedirect(request.getContextPath()
                    + "/system/exception/error/500");
            }
        }
    }

    /**
     * 将参数变量转换为查询条件
     * @param request request
     * @return Map 参数变量转换为查询条件
     */
    public static Map<String, Object> collectRequestParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, Object> map = new HashMap<>();
        //map.put("isValid", "T");
        for (Map.Entry<String, String[]> ent : parameterMap.entrySet()) {
            String key = ent.getKey();
            if(key.startsWith("_"))
                continue;
            String[] values = CollectionsOpt.removeBlankString(ent.getValue());
            if(values==null)
                continue;
            Object paramValue = values.length==1 ? values[0] : values;
            String paramAlias = key;
            /*int e = key.indexOf('-');
            if (e > 0) {
                String pretreatment = key.substring(0, e).trim();
                paramAlias = key.substring(e + 1).trim();
                paramValue = QueryUtils.pretreatParameter(pretreatment,paramValue);
            }*/
            //和 QueryUtils 中的语法保存一致
            int nPos = key.indexOf('(');
            int nRevPos = key.lastIndexOf(')');
            if(nPos>=0 && nRevPos>=0){
                String pretreatment = key.substring(nPos+1, nRevPos).trim();
                paramAlias = nPos>0? key.substring(0,nPos).trim()
                    :key.substring(nRevPos+1).trim();
                paramValue = QueryUtils.pretreatParameter(pretreatment, paramValue);
            }
            map.put(paramAlias, paramValue);
        }
        return map;
    }


}
