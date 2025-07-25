package ch.ivy.addon.portalkit.ivydata.searchcriteria;

import static ch.ivyteam.ivy.workflow.CaseState.CREATED;
import static ch.ivyteam.ivy.workflow.CaseState.DESTROYED;
import static ch.ivyteam.ivy.workflow.CaseState.DONE;
import static ch.ivyteam.ivy.workflow.CaseState.RUNNING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.axonivy.portal.enums.SearchScopeCaseField;

import ch.ivy.addon.portalkit.enums.CaseSortField;
import ch.ivyteam.ivy.workflow.CaseState;
import ch.ivyteam.ivy.workflow.caze.CaseBusinessState;
import ch.ivyteam.ivy.workflow.query.CaseQuery;
import ch.ivyteam.ivy.workflow.query.CaseQuery.IFilterQuery;
import ch.ivyteam.ivy.workflow.query.CaseQuery.OrderByColumnQuery;

public class CaseSearchCriteria {

  public final static List<CaseState> STANDARD_STATES = Arrays.asList(CREATED, RUNNING);
  public final static List<CaseState> ADVANCE_STATES = Arrays.asList(DONE, DESTROYED);
  public final static List<CaseBusinessState> STANDARD_BUSINESS_STATES = Arrays.asList(CaseBusinessState.OPEN);
  public final static List<CaseBusinessState> ADVANCE_BUSINESS_STATES = Arrays.asList(CaseBusinessState.DONE, CaseBusinessState.DESTROYED);

  private List<CaseState> includedStates;
  private String keyword;
  private Long caseId;
  private String category;
  private String sortField;
  private boolean sortDescending;
  private boolean isBusinessCase;
  private boolean isTechnicalCase;
  private Long businessCaseId;
  private boolean isAdminQuery;
  
  private boolean isNewQueryCreated;
  private boolean isSorted = true;
  private CaseQuery customCaseQuery;

  private boolean isGlobalSearch;
  private boolean isGlobalSearchScope;
  private List<SearchScopeCaseField> searchScopeCaseFields;

  public CaseQuery createQuery() {
    CaseQuery finalQuery;
    if (isBusinessCase) {
      finalQuery = CaseQuery.businessCases();
    } else if (isTechnicalCase) {
      finalQuery = CaseQuery.subCases().where().and().businessCaseId().isEqual(businessCaseId);
    } else {
      finalQuery = CaseQuery.create();
    }

    setNewQueryCreated(isNewQueryCreated() || customCaseQuery == null || hasCaseId());
    if (!isNewQueryCreated()) {
      finalQuery.where().andOverall(customCaseQuery);
    }

    if (hasIncludedStates()) {
      finalQuery.where().and(queryForStates(getIncludedStates()));
    }

    if (hasCaseId()) {
      finalQuery.where().and().caseId().isEqual(getCaseId());
    }

    if (hasKeyword()) {
      finalQuery.where().and(queryForKeyword(getKeyword()));
    }

    if (hasCategory()) {
      finalQuery.where().and(queryForCategory(getCategory()));
    }

    if (isSorted) {
      CaseSortingQueryAppender appender = new CaseSortingQueryAppender(finalQuery);
      finalQuery = appender.appendSorting(this).toQuery();
    }
    return finalQuery;
  }
  
  private CaseQuery queryForStates(List<CaseState> states) {
    CaseQuery stateFieldQuery = newCaseQuery();
    IFilterQuery filterQuery = stateFieldQuery.where();
    for (CaseState state : states) {
      filterQuery.or().state().isEqual(state);
    }
    return stateFieldQuery;
  }

  private CaseQuery queryForKeyword(String keyword) {
    String containingKeyword = String.format("%%%s%%", keyword.trim());
    CaseQuery filterByKeywordQuery = newCaseQuery();
    searchScopeCaseFields = Optional.ofNullable(searchScopeCaseFields).orElse(new ArrayList<>());

    if (!isGlobalSearch || (isGlobalSearch && searchScopeCaseFields.contains(SearchScopeCaseField.NAME))) {
      filterByKeywordQuery.where().or().name().isLikeIgnoreCase(containingKeyword);
    }

    if (!isGlobalSearch || (isGlobalSearch && searchScopeCaseFields.contains(SearchScopeCaseField.DESCRIPTION))) {
      filterByKeywordQuery.where().or().description().isLikeIgnoreCase(containingKeyword);
    }

    if (!isGlobalSearch || (isGlobalSearch && searchScopeCaseFields.contains(SearchScopeCaseField.CUSTOM))) {
      filterByKeywordQuery.where().or().customField().anyStringField().isLikeIgnoreCase(containingKeyword);
    }

    try {
      long idKeyword = Long.parseLong(keyword.trim());
      String containingIdKeyword = String.format("%%%d%%", idKeyword);
      filterByKeywordQuery.where().or().caseId().isLike(containingIdKeyword);
    } catch (NumberFormatException e) {
      if (isGlobalSearch()) {
        String containingIdKeyword = String.format("%%%d%%", -1);
        filterByKeywordQuery.where().or().caseId().isLike(containingIdKeyword);
      }
      // do nothing
    }
    return filterByKeywordQuery;
  }

  private CaseQuery queryForCategory(String keyword) {
      String startingWithCategory = String.format("%s%%", keyword);
      return newCaseQuery().where().category().isLike(startingWithCategory);
  }

  private CaseQuery newCaseQuery() {
    if (isBusinessCase) {
      return CaseQuery.businessCases();
    } else if (isTechnicalCase) {
      return CaseQuery.subCases();
    } else {
      return CaseQuery.create();
    }

  }

  private static final class CaseSortingQueryAppender {

    private CaseQuery query;

    public CaseSortingQueryAppender(CaseQuery query) {
      this.query = query;
    }

    public CaseQuery toQuery() {
      return query;
    }

    public CaseSortingQueryAppender appendSorting(CaseSearchCriteria criteria) {
      appendSortByNameIfSet(criteria);
      appendSortByIdIfSet(criteria);
      appendSortByStartTimeIfSet(criteria);
      appendSortByEndTimeIfSet(criteria);
      appendSortByCreatorIfSet(criteria);
      appendSortByStateIfSet(criteria);
      return this;
    }

    private void appendSortByNameIfSet(CaseSearchCriteria criteria) {
      if (!CaseSortField.NAME.toString().equalsIgnoreCase(criteria.getSortField())) {
        return;
      }
      OrderByColumnQuery orderByName = query.orderBy().name();
      if (criteria.isSortDescending()) {
        orderByName.descending();
      }
    }

    private void appendSortByIdIfSet(CaseSearchCriteria criteria) {
      if (!CaseSortField.ID.toString().equalsIgnoreCase(criteria.getSortField())) {
        return;
      }
      OrderByColumnQuery orderByName = query.orderBy().caseId();
      if (criteria.isSortDescending()) {
        orderByName.descending();
      }
    }

    private void appendSortByStartTimeIfSet(CaseSearchCriteria criteria) {
      if (!CaseSortField.CREATION_TIME.toString().equalsIgnoreCase(criteria.getSortField())) {
        return;
      }
      OrderByColumnQuery orderByName = query.orderBy().startTimestamp();
      if (criteria.isSortDescending()) {
        orderByName.descending();
      }
    }

    private void appendSortByEndTimeIfSet(CaseSearchCriteria criteria) {
      if (!CaseSortField.FINISHED_TIME.toString().equalsIgnoreCase(criteria.getSortField())) {
        return;
      }
      OrderByColumnQuery orderByName = query.orderBy().endTimestamp();
      if (criteria.isSortDescending()) {
        orderByName.descending();
      }
    }

    private void appendSortByCreatorIfSet(CaseSearchCriteria criteria) {
      if (!CaseSortField.CREATOR.toString().equalsIgnoreCase(criteria.getSortField())) {
        return;
      }
      OrderByColumnQuery orderByName = query.orderBy().creatorUserDisplayName();
      if (criteria.isSortDescending()) {
        orderByName.descending();
      }
    }
    
    private void appendSortByStateIfSet(CaseSearchCriteria criteria) {
      if (!CaseSortField.STATE.toString().equalsIgnoreCase(criteria.getSortField())) {
        return;
      }
      OrderByColumnQuery orderByName = query.orderBy().state();
      if (criteria.isSortDescending()) {
        orderByName.descending();
      }
    }
  }
  
  /** Check if current user can see task in advance state such as
   * DONE, DESTROYED
   * Then extend Search query for case criteria
   * @param hasAdminPermission
   */
  public void extendStatesQueryByPermission(boolean hasAdminPermission) {
    setAdminQuery(hasAdminPermission);
    if (hasAdminPermission) {
      List<CaseState> adminStateNotIncluded = ADVANCE_STATES.stream()
          .filter(state -> !includedStates.contains(state)).collect(Collectors.toList());
      if (!adminStateNotIncluded.isEmpty()) {
        addIncludedStates(adminStateNotIncluded);
      }
    }
  }

  public List<CaseState> getIncludedStates() {
    return includedStates;
  }
  
  public void addIncludedStates(List<CaseState> includedStates) {
    if (CollectionUtils.isEmpty(includedStates)) {
      this.includedStates = new ArrayList<>();
    }
    this.includedStates.addAll(includedStates);
  }

  public void setIncludedStates(List<CaseState> includedStates) {
    this.includedStates = includedStates;
  }
  
  public String getKeyword() {
    return keyword;
  }

  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  public Long getCaseId() {
    return caseId;
  }

  public void setCaseId(Long caseId) {
    this.caseId = caseId;
  }
  
  public String getSortField() {
    return sortField;
  }

  public void setSortField(String sortField) {
    this.sortField = sortField;
  }

  public boolean isSortDescending() {
    return sortDescending;
  }

  public void setSortDescending(boolean sortDescending) {
    this.sortDescending = sortDescending;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public boolean isBusinessCase() {
    return isBusinessCase;
  }

  public void setBusinessCase(boolean isBusinessCase) {
    this.isBusinessCase = isBusinessCase;
  }

  public boolean isTechnicalCase() {
    return isTechnicalCase;
  }

  public void setTechnicalCase(boolean isTechnicalCase) {
    this.isTechnicalCase = isTechnicalCase;
  }

  public Long getBusinessCaseId() {
    return businessCaseId;
  }

  public void setBusinessCaseId(Long businessCaseId) {
    this.businessCaseId = businessCaseId;
  }

  public boolean isAdminQuery() {
    return isAdminQuery;
  }

  public void setAdminQuery(boolean isAdminQuery) {
    this.isAdminQuery = isAdminQuery;
  }

  public CaseQuery getCustomCaseQuery() {
    return customCaseQuery;
  }

  public void setCustomCaseQuery(CaseQuery customCaseQuery) {
    this.customCaseQuery = customCaseQuery;
  }
  
  public boolean hasIncludedStates() {
    return CollectionUtils.isNotEmpty(includedStates);
  }

  public boolean hasKeyword() {
    return StringUtils.isNotEmpty(keyword);
  }

  public boolean hasCaseId() {
    return caseId != null && caseId != 0;
  }

  public boolean hasCategory() {
    return StringUtils.isNotBlank(category);
  }
  
  public boolean isSorted() {
    return isSorted;
  }

  public void setSorted(boolean isSorted) {
    this.isSorted = isSorted;
  }

  public boolean isNewQueryCreated() {
    return isNewQueryCreated;
  }

  public void setNewQueryCreated(boolean isNewQueryCreated) {
    this.isNewQueryCreated = isNewQueryCreated;
  }

  public List<SearchScopeCaseField> getSearchScopeCaseFields() {
    return searchScopeCaseFields;
  }

  public void setSearchScopeCaseFields(List<SearchScopeCaseField> searchScopeCaseFields) {
    this.searchScopeCaseFields = searchScopeCaseFields;
  }

  public boolean isGlobalSearch() {
    return isGlobalSearch;
  }

  public void setGlobalSearch(boolean isGlobalSearch) {
    this.isGlobalSearch = isGlobalSearch;
  }
  public boolean isGlobalSearchScope() {
    return isGlobalSearchScope;
  }

  public void setGlobalSearchScope(boolean isGlobalSearchScope) {
    this.isGlobalSearchScope = isGlobalSearchScope;
  }
}
