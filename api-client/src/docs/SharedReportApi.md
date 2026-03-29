# SharedReportApi

All URIs are relative to *http://localhost:8888*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getSharedReportHtml**](#getsharedreporthtml) | **GET** /api/v1/shared/{shareToken} | 공유 리포트 조회 (공개)|

# **getSharedReportHtml**
> string getSharedReportHtml()


### Example

```typescript
import {
    SharedReportApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new SharedReportApi(configuration);

let shareToken: string; // (default to undefined)

const { status, data } = await apiInstance.getSharedReportHtml(
    shareToken
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **shareToken** | [**string**] |  | defaults to undefined|


### Return type

**string**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/html, application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

