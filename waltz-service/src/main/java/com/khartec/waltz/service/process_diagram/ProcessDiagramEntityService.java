package com.khartec.waltz.service.process_diagram;

import com.khartec.waltz.data.process_diagram_entity.ProcessDiagramEntityDao;
import com.khartec.waltz.model.process_diagram.ProcessDiagramEntityApplicationAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ProcessDiagramEntityService {

    private final ProcessDiagramEntityDao dao;


    @Autowired
    public ProcessDiagramEntityService(ProcessDiagramEntityDao dao) {
        this.dao = dao;
    }


    public Set<ProcessDiagramEntityApplicationAlignment> findApplicationAlignmentsByDiagramId(Long diagramId){
        return dao.findApplicationAlignmentsByDiagramId(diagramId);
    }
}
