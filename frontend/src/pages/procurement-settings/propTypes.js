import PropTypes from 'prop-types';

export const methodShape = PropTypes.shape({
  id: PropTypes.string,
  code: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  description: PropTypes.string,
  enabled: PropTypes.bool.isRequired,
  minValueMinor: PropTypes.number,
  maxValueMinor: PropTypes.number,
  currency: PropTypes.string.isRequired,
  minBidPeriodDays: PropTypes.number.isRequired,
  requiresPublicAdvert: PropTypes.bool.isRequired,
  requiresJustification: PropTypes.bool.isRequired,
  sortOrder: PropTypes.number.isRequired,
});

export const thresholdShape = PropTypes.shape({
  id: PropTypes.string,
  stage: PropTypes.string.isRequired,
  methodCode: PropTypes.string,
  minAmountMinor: PropTypes.number.isRequired,
  maxAmountMinor: PropTypes.number,
  currency: PropTypes.string.isRequired,
  stepOrder: PropTypes.number.isRequired,
  approverRole: PropTypes.string.isRequired,
  active: PropTypes.bool.isRequired,
});

export const committeeRuleShape = PropTypes.shape({
  id: PropTypes.string,
  methodCode: PropTypes.string,
  minMembers: PropTypes.number.isRequired,
  maxMembers: PropTypes.number.isRequired,
  quorum: PropTypes.number.isRequired,
  twoEnvelope: PropTypes.bool.isRequired,
  technicalPassMarkPct: PropTypes.number,
  requiresCoiDeclaration: PropTypes.bool.isRequired,
  reportSignatories: PropTypes.string.isRequired,
  reportReviewerRole: PropTypes.string,
  awardApproverRole: PropTypes.string.isRequired,
});
