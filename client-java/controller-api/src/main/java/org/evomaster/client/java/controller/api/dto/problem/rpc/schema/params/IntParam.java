package org.evomaster.client.java.controller.api.dto.problem.rpc.schema.params;

import org.evomaster.client.java.controller.api.dto.problem.rpc.schema.dto.ParamDto;
import org.evomaster.client.java.controller.api.dto.problem.rpc.schema.dto.RPCSupportedDataType;
import org.evomaster.client.java.controller.api.dto.problem.rpc.schema.types.PrimitiveOrWrapperType;

/**
 * int param
 */
public class IntParam extends PrimitiveOrWrapperParam<Integer> {

    public IntParam(String name, String type, String fullTypeName) {
        super(name, type, fullTypeName);
    }

    public IntParam(String name, PrimitiveOrWrapperType type) {
        super(name, type);
    }

    @Override
    public ParamDto getDto() {
        ParamDto dto = super.getDto();
        if (getType().isWrapper)
            dto.type.type = RPCSupportedDataType.INT;
        else
            dto.type.type = RPCSupportedDataType.P_INT;
        return dto;
    }

    @Override
    public IntParam copyStructure() {
        return new IntParam(getName(), getType());
    }

    @Override
    public void setValue(ParamDto dto) {
        try {
            setValue(Integer.parseInt(dto.jsonValue));
        }catch (NumberFormatException e){
            throw new RuntimeException("ERROR: fail to convert "+dto.jsonValue+" as int value");
        }

    }
}
