package com.ccerphr.assessment.service;

import com.ccerphr.assessment.dto.DatabaseQueryDTO;
import com.ccerphr.assessment.dto.DatabaseRowUpdateDTO;
import com.ccerphr.assessment.dto.DatabaseTableDTO;
import com.ccerphr.assessment.dto.DatabaseTablePageDTO;

import java.util.List;

public interface DatabaseAdminService {
    List<DatabaseTableDTO> listTables();

    DatabaseTablePageDTO queryTable(DatabaseQueryDTO queryDTO);

    void updateRow(DatabaseRowUpdateDTO updateDTO);

    void deleteRow(String tableName, Long id);
}
