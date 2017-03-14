package org.labkey.immport;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.ExportScriptModel;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RExportScriptFactory;
import org.labkey.api.query.RExportScriptModel;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.ArrayList;
import java.util.List;

public class ImmuneSpaceRExportScriptFactory extends RExportScriptFactory
{
    @Override
    public ExportScriptModel getModel(QueryView queryView)
    {
        return new ImmuneSpaceRExportScriptModel(queryView);
    }

    class ImmuneSpaceRExportScriptModel extends RExportScriptModel
    {
        public ImmuneSpaceRExportScriptModel(QueryView view)
        {
            super(view, "dataset");
        }

        @Override
        public String getScriptExportText()
        {
            // the ImmuneSpaceR export script only applies to containers with the ImmPort module enabled and QueryViews that are study datasets
            Container container = getQueryView().getContainer();
            Study study = container != null ? StudyService.get().getStudy(container) : null;
            boolean isImmPortModuleEnabled = container != null && container.getActiveModules().contains(ModuleLoader.getInstance().getModule(ImmPortModule.class));
            boolean isStudyDataset = study != null && "study".equals(getSchemaName()) && study.getDatasetByName(getQueryName()) != null;
            if (!isImmPortModuleEnabled || !isStudyDataset)
                return super.getScriptExportText();

            return StringUtils.join(getScriptCommands(),"\n");
        }

        public List<String> getScriptCommands()
        {
            Container container = getQueryView().getContainer();
            ArrayList<String> commands = new ArrayList<>();
            List<String> filterExprs = getFilterExpressions();

            StringBuilder sb = new StringBuilder();
            if (!this.clean)
            {
                sb.append("## R Script generated by ").append(getInstallationName()).append(" on ").append(getCreatedOn()).append("\n");
                sb.append("#").append("\n");
                sb.append("# This script makes use of the RGLab/ImmuneSpaceR package.").append("\n");
                sb.append("# See https://github.com/RGLab/ImmuneSpaceR for more information.").append("\n");
                sb.append("\n");
                sb.append("library(ImmuneSpaceR)").append("\n");

                // the Rlabkey package is used to apply filters to the query
                commands.add(sb.toString());
                sb.setLength(0);
            }

            if (!filterExprs.isEmpty())
                commands.add("library(Rlabkey)");
            commands.add("library(ImmuneSpaceR)");

            // if we are not at the project, i.e. individual study container, use the study container name for the connection
            sb.setLength(0);
            String noun = container.isProject() ? "project" : "study";
            if (container.isProject() && container.isDataspace())
                sb.append(noun).append(" <- CreateConnection(\"\")").append("\n");
            else
                sb.append(noun).append(" <- CreateConnection(\"").append(container.getName()).append("\")").append("\n");
            commands.add(sb.toString());
            sb.setLength(0);

            // use the Rlabkey makeFilter function for the colFilter parameter
            String colFilterStr = "";
            if (!filterExprs.isEmpty())
            {
                sb.append("colFilter <- ").append(getFilters()).append("\n");
                commands.add(sb.toString());
                sb.setLength(0);
                colFilterStr = ", colFilter = colFilter";
            }

            // add any column sort
            String colSortStr = "";
            String sort = getSort();
            if (sort != null)
            {
                sb.append("colSort <- ").append(doubleQuote(getSort())).append("\n");
                commands.add(sb.toString());
                sb.setLength(0);
                colSortStr = ", colSort = colSort";
            }

            // use the ImmuneSpaceR getDataset function for the specified query/dataset name and apply any column filters / sorts
            sb.append(variableName + " <- ").append(noun)
                    .append("$getDataset(\"").append(getQueryName()).append("\"")
                    .append(colFilterStr)
                    .append(colSortStr)
                    .append(")");
            commands.add(sb.toString());
            sb.setLength(0);

            if ("rstudio".equals(view))
            {
                commands.add("sprintf(\"" + variableName + " has %d rows(s)\", nrow(" + variableName + "))");
            }

            return commands;
        }
    }
}