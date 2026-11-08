package app.arete.arete_api.service.implementations;

import app.arete.arete_api.api.model.UpdateUserRequestDto;
import app.arete.arete_api.api.model.UserResponseDto;
import app.arete.arete_api.exception.UnauthorizedException;
import app.arete.arete_api.mapper.UserMapper;
import app.arete.arete_api.model.entity.User;
import app.arete.arete_api.repository.UserRepository;
import app.arete.arete_api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String AUTHENTICATION_REQUIRED_DETAIL = "Authentication required";

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public User getAuthenticatedUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException(AUTHENTICATION_REQUIRED_DETAIL);
        }
        return userRepository.findById(parseUserId(authentication.getName()))
                .orElseThrow(() -> new UnauthorizedException(AUTHENTICATION_REQUIRED_DETAIL));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getMe() {
        final User user = getAuthenticatedUser();
        log.debug("Getting profile of user {}", user.getId());
        return userMapper.toUserResponseDto(user);
    }

    @Override
    @Transactional
    public UserResponseDto updateMe(final UpdateUserRequestDto updateUserRequestDto) {
        final User user = getAuthenticatedUser();
        userMapper.updateUserFromDto(updateUserRequestDto, user);
        final User savedUser = userRepository.save(user);
        log.info("User {} updated their profile", savedUser.getId());
        return userMapper.toUserResponseDto(savedUser);
    }

    private static Long parseUserId(final String subject) {
        try {
            return Long.valueOf(subject);
        } catch (final NumberFormatException exception) {
            throw new UnauthorizedException(AUTHENTICATION_REQUIRED_DETAIL);
        }
    }
}
