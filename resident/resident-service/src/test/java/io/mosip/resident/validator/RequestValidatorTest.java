package io.mosip.resident.validator;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import io.mosip.resident.constant.AuthTypeStatus;
import io.mosip.resident.dto.AuthLockOrUnLockRequestDto;
import io.mosip.resident.dto.EuinRequestDTO;
import io.mosip.resident.dto.RequestWrapper;
import io.mosip.resident.exception.InvalidInputException;

@RunWith(MockitoJUnitRunner.class)
@RefreshScope
@ContextConfiguration
public class RequestValidatorTest {

	@InjectMocks
	private RequestValidator requestValidator = new RequestValidator();

	@Before
	public void setup() {
		ReflectionTestUtils.setField(requestValidator, "authLockId", "mosip.resident.authlock");
		ReflectionTestUtils.setField(requestValidator, "euinId", "mosip.resident.euin");
		ReflectionTestUtils.setField(requestValidator, "authTypes", "bio-FIR,bio-IIR");
		ReflectionTestUtils.setField(requestValidator, "version", "v1");
	}

	@Test(expected = InvalidInputException.class)
	public void testValidId() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidUnlockId() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.UNLOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testValideuinId() throws Exception {
		EuinRequestDTO euinRequestDTO = new EuinRequestDTO();
		RequestWrapper<EuinRequestDTO> requestWrapper = new RequestWrapper<>();
		requestWrapper.setRequest(euinRequestDTO);
		requestWrapper.setVersion("v1");
		requestWrapper.setId("mosip.resident.authlock");
		requestValidator.validateEuinRequest(requestWrapper);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidVersion() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setId("mosip.resident.authlock");
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testValideuinVersion() throws Exception {
		EuinRequestDTO euinRequestDTO = new EuinRequestDTO();
		RequestWrapper<EuinRequestDTO> requestWrapper = new RequestWrapper<>();
		requestWrapper.setRequest(euinRequestDTO);
		requestWrapper.setVersion("v2");
		requestWrapper.setId("mosip.resident.euin");
		requestValidator.validateEuinRequest(requestWrapper);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidRequest() throws Exception {

		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setId("mosip.resident.authlock");
		requestWrapper.setVersion("v1");
		requestWrapper.setRequest(null);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testValideuinRequest() throws Exception {

		RequestWrapper<EuinRequestDTO> requestWrapper = new RequestWrapper<>();
		requestWrapper.setVersion("v1");
		requestWrapper.setId("mosip.resident.euin");
		requestValidator.validateEuinRequest(requestWrapper);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidTransactionId() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();

		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setId("mosip.resident.authlock");
		requestWrapper.setVersion("v1");
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidIndividualType() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		authLockRequestDto.setTransactionID("12345");
		authLockRequestDto.setIndividualIdType("RID");
		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setId("mosip.resident.authlock");
		requestWrapper.setVersion("v1");
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testeuinValidIndividualType() throws Exception {
		EuinRequestDTO euinRequestDTO = new EuinRequestDTO();
		euinRequestDTO.setIndividualIdType("RID");
		RequestWrapper<EuinRequestDTO> requestWrapper = new RequestWrapper<>();
		requestWrapper.setRequest(euinRequestDTO);
		requestWrapper.setVersion("v1");
		requestWrapper.setId("mosip.resident.euin");
		requestValidator.validateEuinRequest(requestWrapper);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidOtp() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		authLockRequestDto.setTransactionID("12345");
		authLockRequestDto.setIndividualIdType("UIN");

		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setId("mosip.resident.authlock");
		requestWrapper.setVersion("v1");
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidAuthTypes() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		authLockRequestDto.setTransactionID("12345");
		authLockRequestDto.setIndividualIdType("UIN");
		authLockRequestDto.setOtp("1232354");
		List<String> authTypes = new ArrayList<String>();
		authTypes.add("bio-FMR");
		authLockRequestDto.setAuthType(authTypes);
		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setId("mosip.resident.authlock");
		requestWrapper.setVersion("v1");
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}

	@Test(expected = InvalidInputException.class)
	public void testValidEmptyAuthTypes() throws Exception {
		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		authLockRequestDto.setTransactionID("12345");
		authLockRequestDto.setIndividualIdType("UIN");
		authLockRequestDto.setOtp("1232354");

		RequestWrapper<AuthLockOrUnLockRequestDto> requestWrapper = new RequestWrapper<>();
		requestWrapper.setId("mosip.resident.authlock");
		requestWrapper.setVersion("v1");
		requestWrapper.setRequest(authLockRequestDto);
		requestValidator.validateAuthLockOrUnlockRequest(requestWrapper, AuthTypeStatus.LOCK);

	}
}
