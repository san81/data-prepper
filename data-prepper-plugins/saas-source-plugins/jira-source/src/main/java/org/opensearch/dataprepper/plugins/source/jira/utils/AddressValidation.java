package org.opensearch.dataprepper.plugins.source.jira.utils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.dataprepper.plugins.source.jira.exception.BadRequestException;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import static org.opensearch.dataprepper.plugins.source.jira.utils.Constants.INVALID_URL;


/**
 * This is the AddressValidation Class.
 */

@Slf4j
public class AddressValidation {

    public AddressValidation() {
    }

    /**
     * Method for getInetAddress.
     *
     * @param url input parameter.
     */
    public static InetAddress getInetAddress(String url) {
        try {
            return InetAddress.getByName(new URL(url).getHost());
        } catch (UnknownHostException e) {
            log.error(INVALID_URL, e);
            throw new BadRequestException(e.getMessage(), e);
        } catch (MalformedURLException e) {
            log.error(INVALID_URL, e);
            throw new BadRequestException(e.getMessage(), e);
        }
    }

    /**
     * Validates the InetAddress and throws if the address is any of the following: 1. Link Local
     * Address 2. Loopback
     * Address 3. Multicast Address 4. Any Local Address 5. Site Local Address
     *
     * @param address the {@link InetAddress} to validate.
     * @throws BadRequestException if the address is invalid.
     */
    public static void validateInetAddress(@NonNull final InetAddress address) {
        if (address.isMulticastAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isLoopbackAddress()) {
            throw new BadRequestException(INVALID_URL);
        }
    }
}