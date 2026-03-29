# ReportApi

All URIs are relative to *http://localhost:8888*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**deleteCheatsheet**](#deletecheatsheet) | **DELETE** /api/v1/reports/cheatsheet/{reportId} | 치트시트 삭제|
|[**generateCheatsheet**](#generatecheatsheet) | **POST** /api/v1/reports/cheatsheet/generate | 치트시트 생성 (SSE 스트리밍)|
|[**getCheatsheet**](#getcheatsheet) | **GET** /api/v1/reports/cheatsheet/{reportId} | 치트시트 상세 조회|
|[**listCheatsheets**](#listcheatsheets) | **GET** /api/v1/reports/cheatsheet | 내 치트시트 목록 조회|

# **deleteCheatsheet**
> deleteCheatsheet()


### Example

```typescript
import {
    ReportApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ReportApi(configuration);

let reportId: string; // (default to undefined)

const { status, data } = await apiInstance.deleteCheatsheet(
    reportId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **reportId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**204** | No Content |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **generateCheatsheet**
> Array<any> generateCheatsheet(cheatsheetRequest)


### Example

```typescript
import {
    ReportApi,
    Configuration,
    CheatsheetRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new ReportApi(configuration);

let cheatsheetRequest: CheatsheetRequest; //

const { status, data } = await apiInstance.generateCheatsheet(
    cheatsheetRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **cheatsheetRequest** | **CheatsheetRequest**|  | |


### Return type

**Array<any>**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: text/event-stream


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getCheatsheet**
> ServiceResponseReportResponse getCheatsheet()


### Example

```typescript
import {
    ReportApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ReportApi(configuration);

let reportId: string; // (default to undefined)

const { status, data } = await apiInstance.getCheatsheet(
    reportId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **reportId** | [**string**] |  | defaults to undefined|


### Return type

**ServiceResponseReportResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **listCheatsheets**
> ServiceResponseListReportSummaryResponse listCheatsheets()


### Example

```typescript
import {
    ReportApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ReportApi(configuration);

const { status, data } = await apiInstance.listCheatsheets();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**ServiceResponseListReportSummaryResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

