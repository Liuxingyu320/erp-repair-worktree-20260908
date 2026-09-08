package com.erp.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.erp.common.core.constant.HttpStatus;
import com.erp.common.core.web.domain.AjaxResult;

@DisplayName("全局异常用户提示")
class GlobalExceptionHandlerTest
{
    private static final String INTERNAL_ERROR_MESSAGE = "系统处理失败，请稍后重试或联系管理员";

    @Test
    @DisplayName("上传超过服务端限制时返回明确中文提示")
    void shouldReturnLocalizedMessageForOversizedMultipartRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/signTask/onboard/import/preview");

        AjaxResult result = new GlobalExceptionHandler().handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(10L * 1024L * 1024L), request);

        assertThat(result)
                .containsEntry(AjaxResult.CODE_TAG, HttpStatus.ERROR)
                .containsEntry(AjaxResult.MSG_TAG,
                        "上传文件超过服务端允许大小，请压缩文件或减少内容后重试");
    }

    @Test
    @DisplayName("未知运行时异常不向前端暴露SQL和框架细节")
    void shouldHideInternalRuntimeExceptionDetails()
    {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/inventory/transfer/deliver/7");
        RuntimeException exception = new RuntimeException(
                "### Error updating database; mapper/InvStockLogMapper.xml; SQL: insert into inv_stock_log");

        AjaxResult result = new GlobalExceptionHandler()
                .handleRuntimeException(exception, request);

        assertThat(result)
                .containsEntry(AjaxResult.CODE_TAG, HttpStatus.ERROR)
                .containsEntry(AjaxResult.MSG_TAG, INTERNAL_ERROR_MESSAGE);
        assertThat(String.valueOf(result.get(AjaxResult.MSG_TAG)))
                .doesNotContain("SQL", "Mapper", "inv_stock_log");
    }

    @Test
    @DisplayName("未分类系统异常不向前端暴露内部信息")
    void shouldHideInternalCheckedExceptionDetails()
    {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/inventory/transfer/deliver/7");

        AjaxResult result = new GlobalExceptionHandler().handleException(
                new Exception("database password and internal host"), request);

        assertThat(result)
                .containsEntry(AjaxResult.CODE_TAG, HttpStatus.ERROR)
                .containsEntry(AjaxResult.MSG_TAG, INTERNAL_ERROR_MESSAGE);
    }
}
