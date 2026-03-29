# ComparisonApi

All URIs are relative to *http://localhost:8888*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**compareProducts**](#compareproducts) | **POST** /api/v1/compare | 보험사별 상품 비교 데이터 조회|
|[**generateSummary**](#generatesummary) | **POST** /api/v1/compare/summary | 비교 결과 AI 요약 생성 (SSE)|
|[**getCategories**](#getcategories) | **GET** /api/v1/compare/categories | 비교 가능한 카테고리 목록 조회|

# **compareProducts**
> ServiceResponseComparisonResponse compareProducts(comparisonRequest)


### Example

```typescript
import {
    ComparisonApi,
    Configuration,
    ComparisonRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new ComparisonApi(configuration);

let comparisonRequest: ComparisonRequest; //

const { status, data } = await apiInstance.compareProducts(
    comparisonRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **comparisonRequest** | **ComparisonRequest**|  | |


### Return type

**ServiceResponseComparisonResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **generateSummary**
> Array<any> generateSummary(comparisonRequest)


### Example

```typescript
import {
    ComparisonApi,
    Configuration,
    ComparisonRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new ComparisonApi(configuration);

let comparisonRequest: ComparisonRequest; //

const { status, data } = await apiInstance.generateSummary(
    comparisonRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **comparisonRequest** | **ComparisonRequest**|  | |


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

# **getCategories**
> ServiceResponseListComparisonCategoryInfo getCategories()


### Example

```typescript
import {
    ComparisonApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ComparisonApi(configuration);

const { status, data } = await apiInstance.getCategories();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**ServiceResponseListComparisonCategoryInfo**

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

