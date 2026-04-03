// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * VerifierReputation - Verifier Reputation Tracking Contract
 * 
 * This contract tracks reputation scores for service providers (verifiers) on the Legit platform.
 * Reputation is based on successful verifications, failures, and user rejections.
 */
contract VerifierReputation {
    
    // Events
    event ReputationUpdated(
        bytes32 indexed verifierId,
        uint256 totalVerifications,
        uint256 successfulVerifications,
        uint256 failedVerifications,
        uint256 userRejections,
        uint256 reputationPercentage,
        uint256 timestamp
    );
    
    // Structs
    struct ReputationScore {
        uint256 totalVerifications;
        uint256 successfulVerifications;
        uint256 failedVerifications;
        uint256 userRejections;
        uint256 lastUpdated;
    }
    
    // Storage
    mapping(bytes32 => ReputationScore) public scores;
    
    // Update reputation for a verifier
    function updateReputation(
        bytes32 verifierId,
        bool success,
        bool userRejected
    ) external {
        ReputationScore storage score = scores[verifierId];
        
        score.totalVerifications++;
        
        if (userRejected) {
            score.userRejections++;
        } else if (success) {
            score.successfulVerifications++;
        } else {
            score.failedVerifications++;
        }
        
        score.lastUpdated = block.timestamp;
        
        uint256 reputationPercentage = getReputationPercentage(verifierId);
        
        emit ReputationUpdated(
            verifierId,
            score.totalVerifications,
            score.successfulVerifications,
            score.failedVerifications,
            score.userRejections,
            reputationPercentage,
            block.timestamp
        );
    }
    
    // Get reputation percentage (0-100)
    function getReputationPercentage(bytes32 verifierId) public view returns (uint256) {
        ReputationScore memory score = scores[verifierId];
        
        if (score.totalVerifications == 0) {
            return 100; // New verifiers start with 100%
        }
        
        // Calculate reputation:
        // - Successful verifications: +1 point
        // - Failed verifications: -0.5 points
        // - User rejections: -1 point
        
        uint256 positivePoints = score.successfulVerifications * 100;
        uint256 negativePoints = (score.failedVerifications * 50) + (score.userRejections * 100);
        
        uint256 maxPossiblePoints = score.totalVerifications * 100;
        
        if (negativePoints >= positivePoints) {
            return 0; // Minimum 0%
        }
        
        uint256 netPoints = positivePoints - negativePoints;
        uint256 percentage = (netPoints * 100) / maxPossiblePoints;
        
        return percentage > 100 ? 100 : percentage;
    }
    
    // Get detailed reputation info
    function getReputationDetails(bytes32 verifierId) external view returns (
        uint256 totalVerifications,
        uint256 successfulVerifications,
        uint256 failedVerifications,
        uint256 userRejections,
        uint256 reputationPercentage,
        uint256 lastUpdated
    ) {
        ReputationScore memory score = scores[verifierId];
        return (
            score.totalVerifications,
            score.successfulVerifications,
            score.failedVerifications,
            score.userRejections,
            getReputationPercentage(verifierId),
            score.lastUpdated
        );
    }
    
    // Check if verifier has good reputation (>= 70%)
    function hasGoodReputation(bytes32 verifierId) external view returns (bool) {
        return getReputationPercentage(verifierId) >= 70;
    }
    
    // Get success rate (successful / total, excluding user rejections)
    function getSuccessRate(bytes32 verifierId) external view returns (uint256) {
        ReputationScore memory score = scores[verifierId];
        
        uint256 actualVerifications = score.totalVerifications - score.userRejections;
        
        if (actualVerifications == 0) {
            return 100; // No verifications yet
        }
        
        return (score.successfulVerifications * 100) / actualVerifications;
    }
}
