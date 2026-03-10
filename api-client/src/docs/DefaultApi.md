# DefaultApi

All URIs are relative to *http://localhost:8888*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**devLogin**](#devlogin) | **POST** /api/v1/auth/dev-login | |
|[**getArticleDetail**](#getarticledetail) | **GET** /api/v1/policies/{articleId} | 약관 조문 상세 조회|
|[**getCategories**](#getcategories) | **GET** /api/v1/categories | 카테고리 목록 조회|
|[**getInsuranceCompanies**](#getinsurancecompanies) | **GET** /api/v1/insurance-companies | 보험사 목록 조회|
|[**getMe**](#getme) | **GET** /api/v1/users/me | |
|[**getPdf**](#getpdf) | **GET** /api/v1/policies/pdf/{dataSourceId} | 약관 PDF 파일 조회|
|[**getPremiumExamples**](#getpremiumexamples) | **GET** /api/v1/products/{productId}/premium-examples | 보험료 예시 조회|
|[**getProductIndices**](#getproductindices) | **GET** /api/v1/products/{productId}/indices | 가격지수 조회|
|[**getSuggestions**](#getsuggestions) | **GET** /api/v1/search/suggestions | 추천 검색어 목록|
|[**getSurrenderValues**](#getsurrendervalues) | **GET** /api/v1/products/{productId}/surrender-values | 해약환급금 조회|
|[**login**](#login) | **POST** /api/v1/auth/login | |
|[**logout**](#logout) | **POST** /api/v1/auth/logout | |
|[**refresh**](#refresh) | **POST** /api/v1/auth/refresh | |
|[**search**](#search) | **POST** /api/v1/search | 하이브리드 검색|

# **devLogin**
> ServiceResponseObject devLogin()


### Example

```typescript
import {
    DefaultApi,
    Configuration,
    DevLoginRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let devLoginRequest: DevLoginRequest; // (optional)

const { status, data } = await apiInstance.devLogin(
    devLoginRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **devLoginRequest** | **DevLoginRequest**|  | |


### Return type

**ServiceResponseObject**

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

# **getArticleDetail**
> ServiceResponsePolicyArticleResponse getArticleDetail()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let articleId: string; // (default to undefined)

const { status, data } = await apiInstance.getArticleDetail(
    articleId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **articleId** | [**string**] |  | defaults to undefined|


### Return type

**ServiceResponsePolicyArticleResponse**

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

# **getCategories**
> ServiceResponseListCategoryInfo getCategories()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let insuranceType: 'LIFE' | 'NON_LIFE'; // (optional) (default to undefined)

const { status, data } = await apiInstance.getCategories(
    insuranceType
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **insuranceType** | [**&#39;LIFE&#39; | &#39;NON_LIFE&#39;**]**Array<&#39;LIFE&#39; &#124; &#39;NON_LIFE&#39;>** |  | (optional) defaults to undefined|


### Return type

**ServiceResponseListCategoryInfo**

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

# **getInsuranceCompanies**
> ServiceResponseListInsuranceCompanyResponse getInsuranceCompanies()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

const { status, data } = await apiInstance.getInsuranceCompanies();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**ServiceResponseListInsuranceCompanyResponse**

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

# **getMe**
> ServiceResponseUserResponse getMe()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

const { status, data } = await apiInstance.getMe();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**ServiceResponseUserResponse**

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

# **getPdf**
> File getPdf()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let dataSourceId: string; // (default to undefined)

const { status, data } = await apiInstance.getPdf(
    dataSourceId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **dataSourceId** | [**string**] |  | defaults to undefined|


### Return type

**File**

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

# **getPremiumExamples**
> ServiceResponsePremiumExampleResponse getPremiumExamples()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let productId: string; // (default to undefined)

const { status, data } = await apiInstance.getPremiumExamples(
    productId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **productId** | [**string**] |  | defaults to undefined|


### Return type

**ServiceResponsePremiumExampleResponse**

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

# **getProductIndices**
> ServiceResponseProductIndexResponse getProductIndices()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let productId: string; // (default to undefined)

const { status, data } = await apiInstance.getProductIndices(
    productId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **productId** | [**string**] |  | defaults to undefined|


### Return type

**ServiceResponseProductIndexResponse**

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

# **getSuggestions**
> ServiceResponseListString getSuggestions()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

const { status, data } = await apiInstance.getSuggestions();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**ServiceResponseListString**

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

# **getSurrenderValues**
> ServiceResponseSurrenderValueResponse getSurrenderValues()


### Example

```typescript
import {
    DefaultApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let productId: string; // (default to undefined)

const { status, data } = await apiInstance.getSurrenderValues(
    productId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **productId** | [**string**] |  | defaults to undefined|


### Return type

**ServiceResponseSurrenderValueResponse**

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

# **login**
> ServiceResponseLoginResponse login(loginRequest)


### Example

```typescript
import {
    DefaultApi,
    Configuration,
    LoginRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let loginRequest: LoginRequest; //

const { status, data } = await apiInstance.login(
    loginRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **loginRequest** | **LoginRequest**|  | |


### Return type

**ServiceResponseLoginResponse**

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

# **logout**
> ServiceResponseUnit logout(logoutRequest)


### Example

```typescript
import {
    DefaultApi,
    Configuration,
    LogoutRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let logoutRequest: LogoutRequest; //

const { status, data } = await apiInstance.logout(
    logoutRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **logoutRequest** | **LogoutRequest**|  | |


### Return type

**ServiceResponseUnit**

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

# **refresh**
> ServiceResponseTokenRefreshResponse refresh(tokenRefreshRequest)


### Example

```typescript
import {
    DefaultApi,
    Configuration,
    TokenRefreshRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let tokenRefreshRequest: TokenRefreshRequest; //

const { status, data } = await apiInstance.refresh(
    tokenRefreshRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **tokenRefreshRequest** | **TokenRefreshRequest**|  | |


### Return type

**ServiceResponseTokenRefreshResponse**

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

# **search**
> ServiceResponseSearchResponse search(searchRequest)

키워드+벡터+RRF 기반 약관 검색

### Example

```typescript
import {
    DefaultApi,
    Configuration,
    SearchRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new DefaultApi(configuration);

let searchRequest: SearchRequest; //

const { status, data } = await apiInstance.search(
    searchRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **searchRequest** | **SearchRequest**|  | |


### Return type

**ServiceResponseSearchResponse**

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

