/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.app.ui.search.sidebar;

import de.agilecoders.wicket.extensions.markup.html.bootstrap.form.select.BootstrapSelect;
import de.tudarmstadt.ukp.clarin.webanno.api.*;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.event.RenderAnnotationsEvent;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VMarker;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VTextMarker;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.*;
import de.tudarmstadt.ukp.clarin.webanno.support.spring.ApplicationEventPublisherHolder;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebar_ImplBase;
import de.tudarmstadt.ukp.inception.app.ui.externalsearch.ExternalResultDataProvider;
import de.tudarmstadt.ukp.inception.externalsearch.ExternalSearchResult;
import de.tudarmstadt.ukp.inception.externalsearch.ExternalSearchService;
import de.tudarmstadt.ukp.inception.externalsearch.event.ExternalSearchQueryEvent;
import de.tudarmstadt.ukp.inception.externalsearch.model.DocumentRepository;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.UIMAException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.*;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wicketstuff.event.annotation.OnEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

public class ExternalSearchAnnotationSidebar
    extends AnnotationSidebar_ImplBase
{
    private static final long serialVersionUID = -3358207848681467994L;

    private static final Logger LOG = LoggerFactory.getLogger(ExternalSearchAnnotationSidebar.class);

    private static final String PLAIN_TEXT = "text";

    private @SpringBean DocumentService documentService;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean ProjectService projectService;
    private @SpringBean ExternalSearchService externalSearchService;
    private @SpringBean UserDao userRepository;
    private @SpringBean ImportExportService importExportService;
    private @SpringBean ApplicationEventPublisherHolder applicationEventPublisher;

    final WebMarkupContainer mainContainer;

    private IModel<String> targetQuery = Model.of("");

    private List<ExternalSearchResult> results = new ArrayList<ExternalSearchResult>();

    private IModel<List<DocumentRepository>> repositoriesModel;

    private DocumentRepository currentRepository;

    private ExternalSearchResult selectedResult;

    private Project project;

    private WebMarkupContainer dataTableContainer;

    private ExternalResultDataProvider dataProvider;

    public ExternalSearchAnnotationSidebar(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, JCasProvider aJCasProvider,
            AnnotationPage aAnnotationPage)
    {
        super(aId, aModel, aActionHandler, aJCasProvider, aAnnotationPage);

        project = getModel().getObject().getProject();
        List<DocumentRepository> repositories = externalSearchService
            .listDocumentRepositories(project);

        if (repositories.size() > 0) {
            currentRepository = repositories.get(0);
        }
        else {
            currentRepository = null;
        }

        repositoriesModel = LoadableDetachableModel.of(() -> externalSearchService
            .listDocumentRepositories(project));

        mainContainer = new WebMarkupContainer("mainContainer");
        mainContainer.setOutputMarkupId(true);
        add(mainContainer);

        DocumentRepositorySelectionForm projectSelectionForm = new DocumentRepositorySelectionForm(
            "repositorySelectionForm");
        mainContainer.add(projectSelectionForm);

        SearchForm searchForm = new SearchForm("searchForm");
        add(searchForm);
        mainContainer.add(searchForm);

        List<IColumn<ExternalSearchResult, String>> columns = new ArrayList<>();

        columns.add(new AbstractColumn<ExternalSearchResult, String>(new Model<>("Results"))
        {
            @Override
            public void populateItem(Item<ICellPopulator<ExternalSearchResult>> cellItem,
                String componentId, IModel<ExternalSearchResult> model)
            {
                @SuppressWarnings("rawtypes")
                Item rowItem = cellItem.findParent( Item.class );
                int rowIndex = rowItem.getIndex();
                ResultRowView rowView = new ResultRowView(componentId, rowIndex + 1, model);
                cellItem.add(rowView);
            }
        });

        dataProvider = new ExternalResultDataProvider(
            externalSearchService, userRepository.getCurrentUser(), currentRepository, "merck");

        dataTableContainer = new WebMarkupContainer("dataTableContainer");
        dataTableContainer.setOutputMarkupId(true);
        mainContainer.add(dataTableContainer);

        DataTable<ExternalSearchResult, String> resultTable = new DefaultDataTable<>("resultsTable",
            columns, dataProvider, 8);

        dataTableContainer.add(resultTable);
    }

    private void actionImportDocument(ExternalSearchResult aResult)
    {
        String documentTitle = aResult.getDocumentTitle();

        String text = externalSearchService
            .getDocumentById(userRepository.getCurrentUser(), currentRepository,
                documentTitle)
            .getText();

        if (documentService.existsSourceDocument(project, documentTitle)) {
            error("Document [" + documentTitle + "] already uploaded! Delete "
                + "the document if you want to upload again");
        }
        else {
            importDocument(documentTitle, text);
        }
    }

    private void importDocument(String aFileName, String aText)
    {
        InputStream stream = new ByteArrayInputStream(aText.getBytes(StandardCharsets.UTF_8));

        SourceDocument document = new SourceDocument();
        document.setName(aFileName);
        document.setProject(project);
        document.setFormat(PLAIN_TEXT);

        try (InputStream is = stream) {
            documentService.uploadSourceDocument(is, document);
        }
        catch (IOException | UIMAException e) {
            LOG.error("Unable to retrieve document " + aFileName, e);
            error("Unable to retrieve document " + aFileName + " - "
                + ExceptionUtils.getRootCauseMessage(e));
            e.printStackTrace();
        }

    }

    @OnEvent
    public void onRenderAnnotations(RenderAnnotationsEvent aEvent)
    {
        if (selectedResult != null) {
            AnnotatorState state = aEvent.getState();
            if (state.getWindowBeginOffset() <= selectedResult.getOffsetStart()
                    && selectedResult.getOffsetEnd() <= state.getWindowEndOffset()) {
                aEvent.getVDocument()
                        .add(new VTextMarker(VMarker.MATCH_FOCUS,
                                selectedResult.getOffsetStart() - state.getWindowBeginOffset(),
                                selectedResult.getOffsetEnd() - state.getWindowBeginOffset()));
            }
        }
    }

    private class DocumentRepositorySelectionForm
        extends
        Form<DocumentRepository>
    {
        public DocumentRepositorySelectionForm(String aId)
        {
            super(aId);

            DropDownChoice<DocumentRepository> repositoryCombo =
                new BootstrapSelect<DocumentRepository>(
                    "repositoryCombo",
                    new PropertyModel<DocumentRepository>(ExternalSearchAnnotationSidebar.this,
                        "currentRepository"),
                    repositoriesModel)
                {
                    private static final long serialVersionUID = 1L;

                    {
                        setChoiceRenderer(new ChoiceRenderer<DocumentRepository>("name"));
                        setNullValid(false);
                    }

                    @Override
                    protected CharSequence getDefaultChoice(String aSelectedValue)
                    {
                        return "";
                    }
                };
            // Just update the selection
            repositoryCombo.add(new LambdaAjaxFormComponentUpdatingBehavior("change"));
            add(repositoryCombo);

        }

        private static final long serialVersionUID = -1L;
    }

    private class SearchForm
        extends Form<Void>
    {
        private static final long serialVersionUID = 2186231514180399862L;

        public SearchForm(String id)
        {
            super(id);
            add(new TextField<>("queryInput", targetQuery, String.class));
            LambdaAjaxSubmitLink searchLink = new LambdaAjaxSubmitLink("submitSearch",
                this::actionSearch);
            add(searchLink);
            setDefaultButton(searchLink);
        }

        private void actionSearch(AjaxRequestTarget aTarget, Form aForm)
        {
            selectedResult = null;
            if (targetQuery.getObject() == null) {
                targetQuery.setObject("*.*");
            }

            searchDocuments(targetQuery.getObject());

            dataProvider.searchDocuments(targetQuery.getObject());

            aTarget.add(dataTableContainer);
        }
    }

    private void searchDocuments(String aQuery)
    {
        results.clear();
        applicationEventPublisher.get()
            .publishEvent(new ExternalSearchQueryEvent(this, currentRepository.getProject(),
                userRepository.getCurrentUser().getUsername(), aQuery));

        try {
            for (ExternalSearchResult result : externalSearchService
                .query(userRepository.getCurrentUser(), currentRepository, aQuery)) {
                results.add(result);
            }
        }
        catch (Exception e) {
            LOG.error("Unable to perform query", e);
            error("Unable to load data: " + ExceptionUtils.getRootCauseMessage(e));
        }
    }

    public class ResultRowView
        extends Panel
    {
        private static final long serialVersionUID = -6708211343231617251L;

        public ResultRowView(String id, long rowNumber, IModel<ExternalSearchResult> model)
        {
            super(id, model);

            ExternalSearchResult result = (ExternalSearchResult) getDefaultModelObject();

            String documentTitle = result.getDocumentTitle();

            Whitelist wl = new Whitelist();
            wl.addTags("em");
            Document dirty = Jsoup.parseBodyFragment(result.getHighlights().get(0), "");
            Cleaner cleaner = new Cleaner(wl);
            Document clean = cleaner.clean(dirty);
            clean.select("em").tagName("mark");
            String highlight = clean.body().html();

            boolean existsSourceDocument = documentService.existsSourceDocument(project,
                documentTitle);

            // Import and open annotation
            LambdaAjaxLink link;
            if (!existsSourceDocument) {
                link = new LambdaAjaxLink("titleLink",
                    _target -> {
                        selectedResult = result;
                        actionImportDocument(result);
                        getAnnotationPage().actionShowSelectedDocument(_target,
                            documentService.getSourceDocument(project, documentTitle));
                    });
            } else {
                // open action
                link = new LambdaAjaxLink("titleLink", _target -> {
                    selectedResult = result;
                    getAnnotationPage().actionShowSelectedDocument(_target,
                        documentService.getSourceDocument(project, documentTitle));
                });
            }

            String title = defaultIfBlank(result.getDocumentTitle(),
                defaultIfBlank(result.getDocumentId(),
                    defaultIfBlank(result.getUri(), "<no title>")));


            link.add(new Label("title", title));
            add(link);

            add(new Label("score", result.getScore()));
            add(new Label("highlight", highlight).setEscapeModelStrings(false));
            add(new Label("importStatus", () ->
                existsSourceDocument ? "imported" : "not imported"));
        }
    }
}
