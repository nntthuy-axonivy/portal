package ch.ivy.addon.portal.generic.bean;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import com.axonivy.portal.components.util.FacesMessageUtils;
import com.axonivy.portal.dto.dashboard.filter.DashboardFilter;

import ch.ivy.addon.portalkit.dto.DisplayName;
import ch.ivy.addon.portalkit.dto.dashboard.CaseDashboardWidget;
import ch.ivy.addon.portalkit.dto.dashboard.ColumnModel;
import ch.ivy.addon.portalkit.dto.dashboard.DashboardWidget;
import ch.ivy.addon.portalkit.dto.dashboard.TaskDashboardWidget;
import ch.ivy.addon.portalkit.dto.dashboard.casecolumn.CaseColumnModel;
import ch.ivy.addon.portalkit.dto.dashboard.casecolumn.CreatorColumnModel;
import ch.ivy.addon.portalkit.dto.dashboard.taskcolumn.TaskColumnModel;
import ch.ivy.addon.portalkit.enums.DashboardColumnFormat;
import ch.ivy.addon.portalkit.enums.DashboardColumnType;
import ch.ivy.addon.portalkit.enums.DashboardStandardCaseColumn;
import ch.ivy.addon.portalkit.enums.DashboardStandardTaskColumn;
import ch.ivy.addon.portalkit.enums.DashboardWidgetType;
import ch.ivy.addon.portalkit.ivydata.bo.IvyLanguage;
import ch.ivy.addon.portalkit.ivydata.service.impl.LanguageService;
import ch.ivy.addon.portalkit.service.GlobalSettingService;
import ch.ivy.addon.portalkit.util.DashboardWidgetUtils;
import ch.ivy.addon.portalkit.util.DisplayNameConvertor;
import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.workflow.custom.field.CustomFieldType;
import ch.ivyteam.ivy.workflow.custom.field.ICustomFieldMeta;

@ManagedBean
@ViewScoped
public class ColumnManagementBean implements Serializable, IMultiLanguage {

  private static final long serialVersionUID = -4406460802168467529L;
  private static final String NO_CATEGORY_CMS = "/ch.ivy.addon.portalkit.ui.jsf/common/noCategory";

  private DashboardWidget widget;
  private List<ColumnModel> columnsBeforeSave;
  private List<DashboardColumnType> fieldTypes;
  private DashboardColumnType selectedFieldType;
  private List<String> customFieldCategories;
  private String selectedCustomFieldCategory;
  private Set<ICustomFieldMeta> customFieldNames;
  private ICustomFieldMeta selectedCustomFieldMeta;
  private CustomFieldType selectedCustomFieldType;
  private List<String> fields;
  private String selectedField;
  private String numberFieldPattern;
  private String fieldDisplayName;
  private String fieldDescription;
  private List<DisplayName> fieldDisplayNames;
  private boolean isConfiguredLanguage;

  public void init() {
    this.fieldTypes = Arrays.asList(DashboardColumnType.STANDARD, DashboardColumnType.CUSTOM);
    this.selectedFieldType = DashboardColumnType.STANDARD;
    this.selectedCustomFieldType = CustomFieldType.STRING;
    this.customFieldCategories = null;
    this.selectedCustomFieldCategory = null;
    this.customFieldNames = null;
  }

  public void preRender(DashboardWidget widget) {
    resetValues();
    init();
    this.widget = widget;
    if (widget.getType() == DashboardWidgetType.TASK) {
      TaskDashboardWidget taskWidget = (TaskDashboardWidget) this.widget; 
      this.columnsBeforeSave = new ArrayList<>(taskWidget.getColumns());
      this.fieldTypes = Arrays.asList(DashboardColumnType.STANDARD, DashboardColumnType.CUSTOM,
          DashboardColumnType.CUSTOM_CASE, DashboardColumnType.CUSTOM_BUSINESS_CASE);
    }
    if (widget.getType() == DashboardWidgetType.CASE) {
      CaseDashboardWidget caseDashboardWidget = (CaseDashboardWidget) this.widget;
      this.columnsBeforeSave = new ArrayList<>(caseDashboardWidget.getColumns());
    }
  }

  private void resetValues() {
    this.selectedField = null;
    this.fieldDisplayName = null;
    this.fieldDescription = null;
    this.numberFieldPattern = null;
    this.isConfiguredLanguage = false;
    this.selectedCustomFieldType = CustomFieldType.STRING;
    this.fieldDisplayNames = Collections.emptyList();
  }

  public List<String> completeCategoriesSelection(String query) {
    return getCustomFieldCategories().stream().filter(cat -> StringUtils.containsIgnoreCase(cat, query))
        .collect(Collectors.toList());
  }

  public boolean isEnableHideCaseCreator(ColumnModel column) {
    if (column instanceof CreatorColumnModel) {
      return GlobalSettingService.getInstance().isHideCaseCreator();
    }
    return false;
  }
  
  public void save() {
    if (widget.getType() == DashboardWidgetType.TASK) {
      TaskDashboardWidget taskWidget = (TaskDashboardWidget) this.widget;
      List<TaskColumnModel> taskColumns = new ArrayList<>();
      columnsBeforeSave.forEach(column -> taskColumns.add((TaskColumnModel) column));
      taskWidget.setColumns(taskColumns);
      updateFiltersForTaskWidget(taskWidget);
    } else if (widget.getType() == DashboardWidgetType.CASE) {
      CaseDashboardWidget caseDashboardWidget = (CaseDashboardWidget) this.widget;
      List<CaseColumnModel> caseColumns = new ArrayList<>();
      columnsBeforeSave.forEach(column -> caseColumns.add((CaseColumnModel) column));
      caseDashboardWidget.setColumns(caseColumns);
      updateFiltersForCaseWidget(caseDashboardWidget);
    }
    DashboardWidgetUtils.buildWidgetColumns(widget);
  }

  private void updateFiltersForTaskWidget(TaskDashboardWidget taskWidget) {
    if (CollectionUtils.isEmpty(taskWidget.getColumns())) {
      return;
    }
    List<String> fieldNames = taskWidget.getColumns().stream()
        .map(TaskColumnModel::getField).collect(Collectors.toList());

    List<DashboardFilter> filterToKeep = taskWidget.getFilters().stream()
        .filter(filter -> fieldNames.contains(filter.getField()))
        .collect(Collectors.toList());

    taskWidget.setFilters(filterToKeep);
  }

  private void updateFiltersForCaseWidget(CaseDashboardWidget caseWidget) {
    if (CollectionUtils.isEmpty(caseWidget.getColumns())) {
      return;
    }
    List<String> fieldNames = caseWidget.getColumns().stream()
        .map(CaseColumnModel::getField).collect(Collectors.toList());

    List<DashboardFilter> filterToKeep = caseWidget.getFilters().stream()
        .filter(filter -> fieldNames.contains(filter.getField()))
        .collect(Collectors.toList());

    caseWidget.setFilters(filterToKeep);
  }
  
  public void remove(ColumnModel col) {
    this.columnsBeforeSave
        .removeIf(column -> column.getField().equals(col.getField()) && column.getType() == col.getType());
    fetchFields();
  }

  private List<String> standardFields() {
    List<String> standardFields = new ArrayList<>();
    if (widget.getType() == DashboardWidgetType.TASK) {
      boolean enablePinTask = GlobalSettingService.getInstance().isEnablePinTask();
      for (DashboardStandardTaskColumn col : DashboardStandardTaskColumn.values()) {
        if (!enablePinTask && DashboardStandardTaskColumn.PIN == col) {
          continue;
        }
        standardFields.add(col.getField());
      }
    } else if (widget.getType() == DashboardWidgetType.CASE) {
      var enableCaseOwner = GlobalSettingService.getInstance().isCaseOwnerEnabled();
      for (DashboardStandardCaseColumn col : DashboardStandardCaseColumn.values()) {
        if (!enableCaseOwner && DashboardStandardCaseColumn.OWNER == col) {
          continue;
        }

        standardFields.add(col.getField());
      }
    }
    standardFields.sort(StringUtils::compare);
    return standardFields;
  }

  public void add() {
    ColumnModel columnModel = new ColumnModel();
    if (widget.getType() == DashboardWidgetType.TASK) {
      columnModel = TaskColumnModel.constructColumn(this.selectedFieldType, this.selectedField);
    }
    if (widget.getType() == DashboardWidgetType.CASE) {
      columnModel = CaseColumnModel.constructColumn(this.selectedFieldType, this.selectedField);
    }
    if (!isConfiguredLanguage) {
      updateNameByLocale();
    }
    columnModel.initDefaultValue();
    columnModel.setHeader(this.fieldDisplayName);
    columnModel.setHeaders(this.fieldDisplayNames);
    columnModel.setField(this.selectedField);
    columnModel.setQuickSearch(false);
    if (this.selectedFieldType == DashboardColumnType.CUSTOM
        || this.selectedFieldType == DashboardColumnType.CUSTOM_CASE
        || this.selectedFieldType == DashboardColumnType.CUSTOM_BUSINESS_CASE) {
      columnModel.setType(selectedFieldType);
      columnModel.setFormat(DashboardColumnFormat.valueOf(selectedFieldType.name()));
      columnModel.setPattern(numberFieldPattern);
    }
    if (this.selectedFieldType == DashboardColumnType.CUSTOM_CASE
        || this.selectedFieldType == DashboardColumnType.CUSTOM_BUSINESS_CASE) {
      columnModel.setSortable(null);
    }
    this.columnsBeforeSave.add(columnModel);
    this.fields.remove(columnModel.getField());
    FacesMessage msg = FacesMessageUtils.sanitizedMessage(FacesMessage.SEVERITY_INFO,
        Ivy.cms().co("/ch.ivy.addon.portalkit.ui.jsf/dashboard/fieldIsAdded", Arrays.asList(this.selectedField)),
        null);
    FacesContext.getCurrentInstance().addMessage("field-msg", msg);
    resetValues();
  }

  public void fetchFields() {
    if (selectedFieldType == DashboardColumnType.STANDARD) {
      fields = standardFields();
    } else {
      resetValues();
    }

    this.fields = this.fields.stream().filter(isNotUsedIn(getExistingFieldNames())).collect(Collectors.toList());
  }
  
  public void onSelectType() {
      resetValues();
  }
  
  public boolean isDisplayMultiLanguage() {
    return selectedFieldType == DashboardColumnType.STANDARD && selectedField != null;
  }

  private Predicate<? super String> isNotUsedIn(List<String> existingFields) {
    return f -> CollectionUtils.isEmpty(existingFields) || !existingFields.contains(f);
  }

  private List<String> getExistingFieldNames() {
    List<String> fields = getExistingFields().stream().filter(f -> f.getType() == this.selectedFieldType)
        .map(f -> f.getField()).toList();
    return fields;
  }
  public List<String> completeCustomFields(String query) {
    return getCustomFieldNames().stream()
          .filter(meta -> !meta.isHidden())
          .filter(filterCustomFieldByCategory())
        .map(ICustomFieldMeta::name).filter(isNotUsedIn(getExistingFieldNames()))
          .sorted().filter(f -> StringUtils.containsIgnoreCase(f, query))
          .collect(Collectors.toList());
  }

  public void onSelectCustomField() {
    findCustomFieldMeta().ifPresent(meta -> {
      this.fieldDescription = meta.description();
      this.fieldDisplayName = meta.label();
      this.selectedCustomFieldType = meta.type();
    });
  }

  private Predicate<? super ICustomFieldMeta> filterCustomFieldByCategory() {
    return meta -> {
      if (StringUtils.isNoneBlank(selectedCustomFieldCategory)) {
        if (selectedCustomFieldCategory.equalsIgnoreCase(Ivy.cms().co(NO_CATEGORY_CMS))) {
          return StringUtils.equals(meta.category(), EMPTY);
        }
        return StringUtils.equals(meta.category(), selectedCustomFieldCategory);
      }
      return true;
    };
  }

  public List<String> getCustomFieldCategories() {
    if (widget.getType() == DashboardWidgetType.CASE || selectedFieldType == DashboardColumnType.CUSTOM_CASE
        || this.selectedFieldType == DashboardColumnType.CUSTOM_BUSINESS_CASE) {
      customFieldCategories = ICustomFieldMeta.cases().stream()
            .map(ICustomFieldMeta::category)
            .distinct()
            .sorted().collect(Collectors.toList());
    } else if (widget.getType() == DashboardWidgetType.TASK) {
      customFieldCategories = ICustomFieldMeta.tasks().stream()
            .map(ICustomFieldMeta::category)
            .distinct()
            .sorted().collect(Collectors.toList());
    }
    if (customFieldCategories.contains(EMPTY)) {
      customFieldCategories.remove(EMPTY);
      customFieldCategories.add(Ivy.cms().co(NO_CATEGORY_CMS));
    }
    return customFieldCategories;
  }

  private Set<ICustomFieldMeta> getCustomFieldNames() {
    if (widget.getType() == DashboardWidgetType.TASK) {
      if (selectedFieldType == DashboardColumnType.CUSTOM_CASE
          || selectedFieldType == DashboardColumnType.CUSTOM_BUSINESS_CASE) {
        customFieldNames = ICustomFieldMeta.cases();
      } else {
        customFieldNames = ICustomFieldMeta.tasks();
      }
    } else if (widget.getType() == DashboardWidgetType.CASE) {
      customFieldNames = ICustomFieldMeta.cases();
    }
    return customFieldNames;
  }

  public Optional<ICustomFieldMeta> findCustomFieldMeta() {
    Optional<ICustomFieldMeta> metaData = Optional.empty();
    if (widget.getType() == DashboardWidgetType.TASK) {
      if (selectedFieldType == DashboardColumnType.CUSTOM_CASE
          || selectedFieldType == DashboardColumnType.CUSTOM_BUSINESS_CASE) {
        metaData = ICustomFieldMeta.cases().stream().filter(meta -> meta.name().equals(selectedField)).findFirst();
      } else {
        metaData = ICustomFieldMeta.tasks().stream().filter(meta -> meta.name().equals(selectedField)).findFirst();
      }
    } else if (widget.getType() == DashboardWidgetType.CASE) {
      metaData = ICustomFieldMeta.cases().stream().filter(meta -> meta.name().equals(selectedField)).findFirst();
    }
    return metaData;
  }

  public String getFieldDescription() {
    return fieldDescription;
  }

  public void setFieldDescription(String fieldDescription) {
    this.fieldDescription = fieldDescription;
  }

  public ICustomFieldMeta getSelectedCustomFieldMeta() {
    return selectedCustomFieldMeta;
  }

  public void setSelectedCustomFieldMeta(ICustomFieldMeta selectedCustomFieldMeta) {
    this.selectedCustomFieldMeta = selectedCustomFieldMeta;
  }

  public String getSelectedCustomFieldCategory() {
    return selectedCustomFieldCategory;
  }

  public void setSelectedCustomFieldCategory(String selectedCustomFieldCategory) {
    this.selectedCustomFieldCategory = selectedCustomFieldCategory;
  }

  public CustomFieldType getSelectedCustomFieldType() {
    return selectedCustomFieldType;
  }

  public void setSelectedCustomFieldType(CustomFieldType selectedCustomFieldType) {
    this.selectedCustomFieldType = selectedCustomFieldType;
  }

  public List<ColumnModel> getColumnsBeforeSave() {
    return columnsBeforeSave;
  }

  public void setColumnsBeforeSave(List<ColumnModel> columnsBeforeSave) {
    this.columnsBeforeSave = columnsBeforeSave;
  }

  public List<DashboardColumnType> getFieldTypes() {
    return fieldTypes;
  }

  public void setFieldTypes(List<DashboardColumnType> fieldTypes) {
    this.fieldTypes = fieldTypes;
  }

  public DashboardColumnType getSelectedFieldType() {
    return selectedFieldType;
  }

  public void setSelectedFieldType(DashboardColumnType selectedFieldType) {
    this.selectedFieldType = selectedFieldType;
  }

  public List<String> getFields() {
    return fields;
  }

  public void setStandardFields(List<String> fields) {
    this.fields = fields;
  }

  public String getSelectedField() {
    return selectedField;
  }

  public void setSelectedField(String selectedField) {
    this.selectedField = selectedField;
  }

  public String getNumberFieldPattern() {
    return numberFieldPattern;
  }

  public void setNumberFieldPattern(String numberFieldPattern) {
    this.numberFieldPattern = numberFieldPattern;
  }

  public String getFieldDisplayName() {
    return this.fieldDisplayName;
  }

  public void setFieldDisplayName(String fieldDisplayName) {
    this.fieldDisplayName = fieldDisplayName;
  }

  public List<FetchingField> getExistingFields() {
    return this.columnsBeforeSave.stream().map(column -> new FetchingField(column.getType(), column.getField()))
        .collect(Collectors.toList());
  }

  public void handleVisibility(ColumnModel column) {
    column.setVisible(BooleanUtils.isFalse(column.getVisible()));
  }

  public void handleQuickSearch(ColumnModel column) {
    column.setQuickSearch(BooleanUtils.isFalse(column.getQuickSearch()));
  }
  
  public List<DisplayName> getFieldDisplayNames() {
    return fieldDisplayNames;
  }
  
  public void onSelectStandardField() {
    this.fieldDisplayName = getCurrentDisplayName();
  }

  private String getCurrentDisplayName() {
    if (widget.getType() == DashboardWidgetType.TASK) {
      return Ivy.cms().coLocale(String.format("/Labels/Enums/DashboardStandardTaskColumn/%s", 
          DashboardStandardTaskColumn.findBy(selectedField)), LanguageService.getInstance().getDefaultLanguage());
    } else if (widget.getType() == DashboardWidgetType.CASE) {
      Ivy.cms().coLocale(String.format("/Labels/Enums/DashboardStandardCaseColumn/%s", 
          DashboardStandardCaseColumn.findBy(selectedField)), LanguageService.getInstance().getDefaultLanguage());
    }
    return "";
  }
  
  private void updateFieldDisplayNames(){
    IvyLanguage ivyLanguage = LanguageService.getInstance().getIvyLanguageOfUser();
    List<DisplayName> result = new ArrayList<>();
    for (String language : ivyLanguage.getSupportedLanguages()) {
      DisplayName newItem = new DisplayName();
      newItem.setLocale(Locale.forLanguageTag(language));
      newItem.setValue(StringUtils.defaultIfBlank(getCurrentDisplayName(), this.fieldDisplayName));
      result.add(newItem);
    }
    this.fieldDisplayNames = result;
    this.isConfiguredLanguage = true;
  }

  public void setFieldDisplayNames(List<DisplayName> fieldDisplayNames) {
    this.fieldDisplayNames = fieldDisplayNames;
  }


  public void updateNameByLocale() {
    updateFieldDisplayNames();
    DisplayNameConvertor.setValue(fieldDisplayName, fieldDisplayNames);
  }
  
  public void updateCurrentLanguage() {
    this.fieldDisplayName = DisplayNameConvertor.updateCurrentValue(fieldDisplayName, fieldDisplayNames);
  }

  public boolean isConfiguredLanguage() {
    return isConfiguredLanguage;
  }

  public void setConfiguredLanguage(boolean isConfiguredLanguage) {
    this.isConfiguredLanguage = isConfiguredLanguage;
  }

  public class FetchingField {
    private DashboardColumnType type;
    private String field;

    public FetchingField(DashboardColumnType type, String field) {
      this.type = type;
      this.field = field;
    }

    public DashboardColumnType getType() {
      return type;
    }

    public void setType(DashboardColumnType type) {
      this.type = type;
    }

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }
  }
}
