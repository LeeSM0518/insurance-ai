# DefaultApi

All URIs are relative to *http://localhost:8888*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**devLogin**](#devlogin) | **POST** /api/v1/auth/dev-login | |
|[**getMe**](#getme) | **GET** /api/v1/users/me | |
|[**login**](#login) | **POST** /api/v1/auth/login | |
|[**logout**](#logout) | **POST** /api/v1/auth/logout | |
|[**refresh**](#refresh) | **POST** /api/v1/auth/refresh | |

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

